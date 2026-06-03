package com.amplitude.core

import com.amplitude.id.IdentityManager

/**
 * Owns **runtime** identity (userId/deviceId) for one [Amplitude] instance. The two-layer model:
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
    private var pendingDeviceId: Pending<String>? = null

    @JvmInline
    private value class Pending<T>(val value: T)

    private fun <T> Pending<T>?.getOrElse(default: T?) = if (this != null) value else default

    fun setUserId(userId: String?) {
        synchronized(lock) {
            state.userId = userId
            identityManager?.editIdentity()?.setUserId(userId)?.commit()
                ?: run { pendingUserId = Pending(userId) }
        }
    }

    fun setDeviceId(deviceId: String) {
        synchronized(lock) {
            state.deviceId = deviceId
            identityManager?.editIdentity()?.setDeviceId(deviceId)?.commit()
                ?: run { pendingDeviceId = Pending(deviceId) }
        }
    }

    /**
     * Reset clears userId and rotates deviceId as **one** locked mutation, so the two fields
     * never tear apart and persistence commits once. Callers fan out callbacks after this
     * returns (lock released) — never from inside.
     */
    fun reset(newDeviceId: String) {
        synchronized(lock) {
            state.userId = null
            state.deviceId = newDeviceId
            val editor = identityManager?.editIdentity()
            if (editor != null) {
                editor.setUserId(null).setDeviceId(newDeviceId).commit()
            } else {
                pendingUserId = Pending(null)
                pendingDeviceId = Pending(newDeviceId)
            }
        }
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
