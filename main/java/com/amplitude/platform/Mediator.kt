package com.amplitude.platform

import com.amplitude.events.BaseEvent
import com.amplitude.events.GroupIdentifyEvent
import com.amplitude.events.IdentifyEvent
import com.amplitude.events.RevenueEvent

internal class Mediator(private val plugins: MutableList<Plugin>) {
    fun add(plugin: Plugin) = synchronized(plugins) {
        plugins.add(plugin)
    }

    fun remove(plugin: Plugin) = synchronized(plugins) {
        plugins.removeAll { it === plugin }
    }

    fun execute(event: BaseEvent): BaseEvent? = synchronized(plugins) {
        var result: BaseEvent? = event

        plugins.forEach { plugin ->
            if (result != null) {
                when (plugin) {
                    is DestinationPlugin -> {
                        try {
                            plugin.process(result)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    is EventPlugin -> {
                        result = plugin.execute(result as BaseEvent)
                        when (result) {
                            is IdentifyEvent -> {
                                result = plugin.identify(result as IdentifyEvent)
                            }
                            is GroupIdentifyEvent -> {
                                result = plugin.groupIdentify(result as GroupIdentifyEvent)
                            }
                            is RevenueEvent -> {
                                result = plugin.revenue(result as RevenueEvent)
                            }
                            is BaseEvent -> {
                                result = plugin.track(result as BaseEvent)
                            }
                        }
                    }
                    else -> {
                        result = plugin.execute(result as BaseEvent)
                    }
                }
            }
        }
        return result
    }
}
