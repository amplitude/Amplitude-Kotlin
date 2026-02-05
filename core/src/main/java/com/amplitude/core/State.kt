package com.amplitude.core

import com.amplitude.core.platform.ObservePlugin
import com.amplitude.id.Identity

class State {
    private val lock = Any()
    private var _identity: Identity = Identity()

    var identity: Identity
        get() = synchronized(lock) { _identity }
        set(value) {
            val changeInfo =
                synchronized(lock) {
                    computeIdentityChange(value)
                } ?: return
            notifyObservers(changeInfo)
        }

    // Delegating property for backwards compatibility
    var userId: String?
        get() = synchronized(lock) { _identity.userId }
        set(value) {
            val changeInfo =
                synchronized(lock) {
                    computeIdentityChange(Identity(userId = value, deviceId = _identity.deviceId))
                } ?: return
            notifyObservers(changeInfo)
        }

    // Delegating property for backwards compatibility
    var deviceId: String?
        get() = synchronized(lock) { _identity.deviceId }
        set(value) {
            val changeInfo =
                synchronized(lock) {
                    computeIdentityChange(Identity(userId = _identity.userId, deviceId = value))
                } ?: return
            notifyObservers(changeInfo)
        }

    /**
     * Compute identity change info. Must be called while holding [lock].
     * Returns null if no change occurred (idempotent).
     */
    private fun computeIdentityChange(newIdentity: Identity): IdentityChangeInfo? {
        val oldIdentity = _identity
        if (oldIdentity == newIdentity) return null

        _identity = newIdentity

        return IdentityChangeInfo(
            newIdentity = newIdentity,
            plugins = plugins.toList(),
            userIdChanged = oldIdentity.userId != newIdentity.userId,
            deviceIdChanged = oldIdentity.deviceId != newIdentity.deviceId,
        )
    }

    /**
     * Notify observers about identity change. Called outside the lock.
     */
    private fun notifyObservers(changeInfo: IdentityChangeInfo) {
        changeInfo.plugins.forEach { plugin ->
            if (changeInfo.userIdChanged) plugin.onUserIdChanged(changeInfo.newIdentity.userId)
            if (changeInfo.deviceIdChanged) plugin.onDeviceIdChanged(changeInfo.newIdentity.deviceId)
            plugin.onIdentityChanged(changeInfo.newIdentity)
        }
    }

    private data class IdentityChangeInfo(
        val newIdentity: Identity,
        val plugins: List<ObservePlugin>,
        val userIdChanged: Boolean,
        val deviceIdChanged: Boolean,
    )

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
