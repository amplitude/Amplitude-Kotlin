package com.amplitude.kotlin.platform

import com.amplitude.kotlin.events.BaseEvent
import com.amplitude.kotlin.events.GroupIdentifyEvent
import com.amplitude.kotlin.events.IdentifyEvent
import com.amplitude.kotlin.events.RevenueEvent

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
                        plugin.process(result)
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
                                result = plugin.logRevenue(result as RevenueEvent)
                            }
                            is BaseEvent -> {
                                result = plugin.logEvent(result as BaseEvent)
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