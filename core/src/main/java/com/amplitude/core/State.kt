package com.amplitude.core

import com.amplitude.core.platform.ObservePlugin

class State {
    var userId: String? = null
        set(value: String?) {
            field = value
            plugins.forEach { plugin ->
                plugin.onUserIdChanged(value)
            }
        }

    var deviceId: String? = null
        set(value: String?) {
            field = value
            plugins.forEach { plugin ->
                plugin.onDeviceIdChanged(value)
            }
        }

    var sessionId: Long? = null
        set(value: Long?) {
            field = value
            plugins.forEach { plugin ->
                plugin.onSessionIdChanged(value)
            }
        }

    val plugins: MutableList<ObservePlugin> = mutableListOf()

    fun add(plugin: ObservePlugin, amplitude: Amplitude) = synchronized(plugins) {
        plugin.setup(amplitude)
        plugins.add(plugin)
    }

    fun remove(plugin: ObservePlugin) = synchronized(plugins) {
        plugins.removeAll { it === plugin }
    }
}
