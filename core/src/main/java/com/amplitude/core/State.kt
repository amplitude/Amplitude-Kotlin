package com.amplitude.core

import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.platform.Plugin

class State {
    /**
     * General-purpose callback invoked whenever userId or deviceId changes.
     * Set by [Amplitude] to propagate identity changes to all timeline plugins.
     */
    internal var onIdentityChanged: ((userId: String?, deviceId: String?, type: IdentityChangeType) -> Unit)? = null

    var userId: String? = null
        set(value: String?) {
            field = value
            plugins.forEach { plugin ->
                plugin.onUserIdChanged(value)
            }
            onIdentityChanged?.invoke(value, deviceId, IdentityChangeType.USER_ID)
        }

    var deviceId: String? = null
        set(value: String?) {
            field = value
            plugins.forEach { plugin ->
                plugin.onDeviceIdChanged(value)
            }
            onIdentityChanged?.invoke(userId, value, IdentityChangeType.DEVICE_ID)
        }

    val plugins: MutableList<ObservePlugin> = mutableListOf()

    fun add(
        plugin: ObservePlugin,
        amplitude: Amplitude,
    ) = synchronized(plugins) {
        plugin.setup(amplitude)
        plugins.add(plugin)
    }

    fun remove(plugin: ObservePlugin) =
        synchronized(plugins) {
            plugins.removeAll { it === plugin }
        }

    /**
     * Remove any [ObservePlugin] whose [Plugin.name] matches the given [name],
     * calling [Plugin.teardown] on each removed plugin.
     */
    fun removeByName(name: String) =
        synchronized(plugins) {
            val iterator = plugins.iterator()
            while (iterator.hasNext()) {
                val plugin = iterator.next()
                if (plugin.name == name) {
                    iterator.remove()
                    plugin.teardown()
                }
            }
        }

    internal enum class IdentityChangeType {
        USER_ID,
        DEVICE_ID,
    }
}
