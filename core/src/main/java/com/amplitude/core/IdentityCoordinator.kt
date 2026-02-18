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
    private var hasPendingUserId = false
    private var pendingUserId: String? = null
    private var hasPendingDeviceId = false
    private var pendingDeviceId: String? = null

    fun setUserId(userId: String?) {
        synchronized(lock) {
            state.userId = userId
            identityManager?.editIdentity()?.setUserId(userId)?.commit()
                ?: run {
                    hasPendingUserId = true
                    pendingUserId = userId
                }
        }
    }

    fun setDeviceId(deviceId: String) {
        synchronized(lock) {
            state.deviceId = deviceId
            identityManager?.editIdentity()?.setDeviceId(deviceId)?.commit()
                ?: run {
                    hasPendingDeviceId = true
                    pendingDeviceId = deviceId
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

            val userId = if (hasPendingUserId) pendingUserId else persisted.userId
            val deviceId = if (hasPendingDeviceId) pendingDeviceId else persisted.deviceId

            state.userId = userId
            state.deviceId = deviceId

            identityManager.editIdentity()
                .setUserId(userId)
                .setDeviceId(deviceId)
                .commit()

            hasPendingUserId = false
            hasPendingDeviceId = false
            pendingUserId = null
            pendingDeviceId = null
        }
    }
}
