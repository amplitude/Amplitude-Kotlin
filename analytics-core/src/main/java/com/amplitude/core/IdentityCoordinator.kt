package com.amplitude.core

import com.amplitude.core.events.IdentifyOperation
import com.amplitude.id.IdentityManager

private val identifyOperationTypes = IdentifyOperation.values().map { it.operationType }.toSet()

/**
 * Owns **runtime** identity (userId/deviceId/user properties) for one [Amplitude] instance. The two-layer model:
 *
 *  - [IdentityCoordinator] (here): the in-memory mirror in [State] + pre-build pending intent +
 *    bootstrap reconciliation. Every mutation is serialized on [lock] so the State write and the
 *    persistence commit are atomic. Before build there is no [IdentityManager], so touched fields
 *    are remembered as pending; [bootstrap] reconciles them against persisted values (user intent
 *    wins) once the manager binds.
 *  - [IdentityManager] (com.amplitude.id, shared with Experiment): durable persistence only.
 *
 * Notification is intentionally **not** here. The coordinator only mutates and returns; [Amplitude]
 * fans plugin callbacks out *after* [lock] is released, which is what makes a reentrant
 * setUserId/setDeviceId from inside a callback safe (the inner write can't be clobbered by the
 * outer commit). Keep this class free of plugin/callback concerns.
 */
internal class IdentityCoordinator internal constructor(private val state: State) {
    private val lock = Any()
    private var identityManager: IdentityManager? = null
    private var pendingUserId: Pending<String?>? = null
    private var pendingDeviceId: Pending<String?>? = null

    fun snapshot(): IdentitySnapshot =
        synchronized(lock) {
            IdentitySnapshot(state.userId, state.deviceId, state.userProperties)
        }

    /** Replaces the complete identity, retaining explicit null ids until bootstrap. */
    fun setIdentity(identity: AnalyticsIdentity): IdentityChange? {
        val next = IdentitySnapshot(identity.userId, identity.deviceId, identity.userProperties)
        return synchronized(lock) {
            val previous = snapshot()
            if (identityManager == null) {
                pendingUserId = Pending(next.userId)
                pendingDeviceId = Pending(next.deviceId)
            }
            val change = IdentityChange(previous, next)
            if (!change.idsChanged && !change.propertiesChanged) return@synchronized null
            state.userId = next.userId
            state.deviceId = next.deviceId
            state.userProperties = next.userProperties
            if (change.idsChanged) {
                identityManager?.editIdentity()
                    ?.setUserId(next.userId)
                    ?.setDeviceId(next.deviceId)
                    ?.commit()
            }
            change
        }
    }

    /**
     * Applies the locally supported Identify operations without persisting user properties.
     * Returns whether the properties changed.
     */
    fun applyUserProperties(operations: Map<String, Any?>): Boolean =
        synchronized(lock) {
            val updated = state.userProperties.toMutableMap()
            if (operations.containsKey("\$clearAll")) updated.clear()
            (operations["\$unset"] as? Map<*, *>)?.keys?.forEach { updated.remove(it) }
            (operations["\$set"] as? Map<*, *>)?.forEach { (key, value) ->
                if (key is String) updated[key] = value
            }
            operations.forEach { (key, value) ->
                if (key !in identifyOperationTypes) updated[key] = value
            }
            val changed = state.userProperties != updated
            if (changed) state.userProperties = immutableProperties(updated)
            changed
        }

    /**
     * Applies Identify option ids and supported property [operations] as one change, so
     * callers can notify plugins once with the complete identity. Supplied ids commit together,
     * even when unchanged. Returns null when nothing changed.
     */
    fun identify(
        userId: String?,
        deviceId: String?,
        operations: Map<String, Any?>?,
    ): IdentityChange? =
        synchronized(lock) {
            val previous = snapshot()
            userId?.let { state.userId = it }
            deviceId?.let { state.deviceId = it }
            operations?.let { applyUserProperties(it) }
            if (userId != null || deviceId != null) {
                val editor = identityManager?.editIdentity()
                if (editor != null) {
                    userId?.let { editor.setUserId(it) }
                    deviceId?.let { editor.setDeviceId(it) }
                    editor.commit()
                } else {
                    userId?.let { pendingUserId = Pending(it) }
                    deviceId?.let { pendingDeviceId = Pending(it) }
                }
            }
            IdentityChange(previous, snapshot()).takeIf { it.idsChanged || it.propertiesChanged }
        }

    @JvmInline
    private value class Pending<T>(val value: T)

    private fun <T> Pending<T>?.getOrElse(default: T?) = if (this != null) value else default

    /** Returns whether the user id changed. */
    fun setUserId(userId: String?): Boolean =
        synchronized(lock) {
            val changed = state.userId != userId
            state.userId = userId
            identityManager?.editIdentity()?.setUserId(userId)?.commit()
                ?: run { pendingUserId = Pending(userId) }
            changed
        }

    /** Returns whether the device id changed. */
    fun setDeviceId(deviceId: String): Boolean =
        synchronized(lock) {
            val changed = state.deviceId != deviceId
            state.deviceId = deviceId
            identityManager?.editIdentity()?.setDeviceId(deviceId)?.commit()
                ?: run { pendingDeviceId = Pending(deviceId) }
            changed
        }

    /**
     * Reset clears userId and rotates deviceId as **one** locked mutation, so the two fields
     * never tear apart and persistence commits once. Callers fan out callbacks after this
     * returns (lock released) — never from inside.
     */
    fun reset(newDeviceId: String): IdentityChange =
        synchronized(lock) {
            val previous = snapshot()
            state.userId = null
            state.deviceId = newDeviceId
            state.userProperties = emptyMap()
            val editor = identityManager?.editIdentity()
            if (editor != null) {
                editor.setUserId(null).setDeviceId(newDeviceId).commit()
            } else {
                pendingUserId = Pending(null)
                pendingDeviceId = Pending(newDeviceId)
            }
            IdentityChange(previous, snapshot())
        }

    /**
     * Called during [com.amplitude.core.Amplitude.createIdentityContainer] to reconcile
     * persisted identity with any pre-build changes. User intent wins over persisted values.
     */
    internal fun bootstrap(identityManager: IdentityManager) {
        synchronized(lock) {
            this.identityManager = identityManager
            val persisted = identityManager.getIdentity()

            val userId = pendingUserId.getOrElse(persisted.userId)
            val deviceId = pendingDeviceId.getOrElse(persisted.deviceId)

            state.userId = userId
            state.deviceId = deviceId

            identityManager.editIdentity()
                .setUserId(userId)
                .setDeviceId(deviceId)
                .commit()

            pendingUserId = null
            pendingDeviceId = null
        }
    }
}

internal class IdentityChange(val previous: AnalyticsIdentity, val current: IdentitySnapshot) {
    val userIdChanged = previous.userId != current.userId
    val deviceIdChanged = previous.deviceId != current.deviceId
    val idsChanged = userIdChanged || deviceIdChanged
    val propertiesChanged = previous.userProperties != current.userProperties
}
