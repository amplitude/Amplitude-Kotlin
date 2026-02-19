package com.amplitude.core

import com.amplitude.id.IdentityManager

/**
 * Coordinates identity (userId/deviceId) updates between [State] and [IdentityManager].
 *
 * All mutations go through synchronized blocks to ensure atomic state + persistence.
 * Before build, only [State] is updated and pending flags are set.
 * During [bootstrap], persisted identity is reconciled with any pre-build changes.
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
