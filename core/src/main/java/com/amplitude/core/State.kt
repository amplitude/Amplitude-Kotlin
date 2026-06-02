package com.amplitude.core

import com.amplitude.core.platform.ObservePlugin
import java.util.EnumSet

class State {
    internal enum class IdentityChange {
        USER_ID,
        DEVICE_ID,
    }

    internal var onIdentityChanged: ((EnumSet<IdentityChange>) -> Unit)? = null

    private var _userId: String? = null
    private var _deviceId: String? = null

    var userId: String?
        get() = _userId
        set(value) {
            _userId = value
            notifyIdentityChanged(IdentityChange.USER_ID)
        }

    var deviceId: String?
        get() = _deviceId
        set(value) {
            _deviceId = value
            notifyIdentityChanged(IdentityChange.DEVICE_ID)
        }

    internal fun setUserIdSilently(value: String?) {
        _userId = value
    }

    internal fun setDeviceIdSilently(value: String?) {
        _deviceId = value
    }

    val plugins: MutableList<ObservePlugin> = mutableListOf()

    fun add(
        plugin: ObservePlugin,
        amplitude: Amplitude,
    ): Boolean {
        plugin.setup(amplitude)
        return synchronized(plugins) {
            plugins.add(plugin)
        }
    }

    fun remove(plugin: ObservePlugin): Boolean {
        val removed =
            synchronized(plugins) {
                plugins.removeAll { it === plugin }
            }
        if (removed) plugin.teardown()
        return removed
    }

    internal fun removeByName(name: String): List<ObservePlugin> =
        synchronized(plugins) {
            val removed = plugins.filter { it.name == name }
            plugins.removeAll(removed)
            removed
        }

    internal fun pluginsSnapshot(): List<ObservePlugin> =
        synchronized(plugins) {
            plugins.toList()
        }

    internal fun notifyIdentityChanged(changes: EnumSet<IdentityChange>) {
        if (changes.isEmpty()) return
        onIdentityChanged?.invoke(changes)
    }

    private fun notifyIdentityChanged(change: IdentityChange) {
        notifyIdentityChanged(EnumSet.of(change))
    }
}
