package com.amplitude

import com.amplitude.platform.ObservePlugin

class State {
    var userId: String? = null
        set(value: String?) {
            userId = value
            plugins.forEach { plugin ->
                plugin.onUserIdChanged(value)
            }
        }

    var deviceId: String? = null
        set(value: String?) {
            deviceId = value
            plugins.forEach { plugin ->
                plugin.onDeviceIdChanged(value)
            }
        }
    val plugins: MutableList<ObservePlugin> = mutableListOf()

    fun add(plugin: ObservePlugin) = synchronized(plugins) {
        plugins.add(plugin)
    }

    fun remove(plugin: ObservePlugin) = synchronized(plugins) {
        plugins.removeAll { it === plugin }
    }
}