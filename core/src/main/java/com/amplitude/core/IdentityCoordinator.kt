package com.amplitude.core

import com.amplitude.id.IdentityManager
import java.util.EnumSet

/**
 * Coordinates identity (userId/deviceId) updates between [State] and [IdentityManager].
 *
 * All mutations go through synchronized blocks to ensure atomic state + persistence.
 * Before build, only [State] is updated and pending flags are set.
 * During [bootstrap], persisted identity is reconciled with any pre-build changes.
 *
 * Notifications are deliberately not sent while [lock] is held. Plugin callbacks
 * can re-enter identity APIs, and JVM monitors are reentrant, so notifying inside
 * the lock can let an outer call overwrite an inner callback's newer value.
 */
internal class IdentityCoordinator internal constructor(private val state: State) {
    private val lock = Any()
    private var identityManager: IdentityManager? = null
    private var pendingUserId: Pending<String?>? = null
    private var pendingDeviceId: Pending<String>? = null

    @JvmInline
    private value class Pending<T>(val value: T)

    private fun <T> Pending<T>?.getOrElse(default: T?) = if (this != null) value else default

    fun setUserId(userId: String?): EnumSet<State.IdentityChange> {
        synchronized(lock) {
            state.setUserIdSilently(userId)
            identityManager?.editIdentity()?.setUserId(userId)?.commit()
                ?: run { pendingUserId = Pending(userId) }
        }
        return EnumSet.of(State.IdentityChange.USER_ID)
    }

    fun setDeviceId(deviceId: String): EnumSet<State.IdentityChange> {
        synchronized(lock) {
            state.setDeviceIdSilently(deviceId)
            identityManager?.editIdentity()?.setDeviceId(deviceId)?.commit()
                ?: run { pendingDeviceId = Pending(deviceId) }
        }
        return EnumSet.of(State.IdentityChange.DEVICE_ID)
    }

    fun resetIdentity(newDeviceId: String): EnumSet<State.IdentityChange> {
        synchronized(lock) {
            state.setUserIdSilently(null)
            state.setDeviceIdSilently(newDeviceId)
            identityManager?.editIdentity()
                ?.setUserId(null)
                ?.setDeviceId(newDeviceId)
                ?.commit()
                ?: run {
                    pendingUserId = Pending(null)
                    pendingDeviceId = Pending(newDeviceId)
                }
        }
        return EnumSet.of(State.IdentityChange.USER_ID, State.IdentityChange.DEVICE_ID)
    }

    /**
     * Called during [com.amplitude.core.Amplitude.createIdentityContainer] to reconcile
     * persisted identity with any pre-build changes. User intent wins over persisted values.
     */
    internal fun bootstrap(identityManager: IdentityManager): EnumSet<State.IdentityChange> {
        synchronized(lock) {
            this.identityManager = identityManager
            val persisted = identityManager.getIdentity()

            val userId = pendingUserId.getOrElse(persisted.userId)
            val deviceId = pendingDeviceId.getOrElse(persisted.deviceId)

            state.setUserIdSilently(userId)
            state.setDeviceIdSilently(deviceId)

            identityManager.editIdentity()
                .setUserId(userId)
                .setDeviceId(deviceId)
                .commit()

            pendingUserId = null
            pendingDeviceId = null
        }
        return EnumSet.of(State.IdentityChange.USER_ID, State.IdentityChange.DEVICE_ID)
    }
}
