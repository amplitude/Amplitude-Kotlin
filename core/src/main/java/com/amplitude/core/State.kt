package com.amplitude.core

import com.amplitude.core.platform.ObservePlugin
import com.amplitude.id.Identity

class State {
    private val lock = Any()
    private var _identity: Identity = Identity()

    var identity: Identity
        get() = synchronized(lock) { _identity }
        set(value) = setIdentityInternal(value)

    // Delegating property for backwards compatibility
    var userId: String?
        get() = synchronized(lock) { _identity.userId }
        set(value) {
            synchronized(lock) {
                applyIdentityChange(Identity(userId = value, deviceId = _identity.deviceId))
            }
        }

    // Delegating property for backwards compatibility
    var deviceId: String?
        get() = synchronized(lock) { _identity.deviceId }
        set(value) {
            synchronized(lock) {
                applyIdentityChange(Identity(userId = _identity.userId, deviceId = value))
            }
        }

    private fun setIdentityInternal(newIdentity: Identity) {
        synchronized(lock) {
            applyIdentityChange(newIdentity)
        }
    }

    /**
     * Apply an identity change. Must be called while holding [lock].
     */
    private fun applyIdentityChange(newIdentity: Identity) {
        val oldIdentity = _identity
        if (oldIdentity == newIdentity) return // Idempotent

        _identity = newIdentity

        val userIdChanged = oldIdentity.userId != newIdentity.userId
        val deviceIdChanged = oldIdentity.deviceId != newIdentity.deviceId

        plugins.toList().forEach { plugin ->
            if (userIdChanged) plugin.onUserIdChanged(newIdentity.userId)
            if (deviceIdChanged) plugin.onDeviceIdChanged(newIdentity.deviceId)
            plugin.onIdentityChanged(newIdentity)
        }
    }

    val plugins: MutableList<ObservePlugin> = mutableListOf()

    fun add(
        plugin: ObservePlugin,
        amplitude: Amplitude,
    ) = synchronized(lock) {
        plugin.setup(amplitude)
        plugins.add(plugin)
    }

    fun remove(plugin: ObservePlugin) =
        synchronized(lock) {
            plugins.removeAll { it === plugin }
        }
}
