package com.amplitude.core.platform

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.plugins.UniversalPlugin
import java.util.concurrent.CopyOnWriteArrayList

internal class Mediator(
    val plugins: CopyOnWriteArrayList<UniversalPlugin> = CopyOnWriteArrayList()
) {
    fun add(plugin: UniversalPlugin) {
        plugins.add(plugin)
    }

    fun remove(plugin: UniversalPlugin) = plugins.removeAll { it === plugin }

    fun execute(event: BaseEvent): BaseEvent? {
            return plugins.fold(event as BaseEvent?) { acc, plugin ->
                acc?.let { currentEvent ->
                    try {
                        when (plugin) {
                            is DestinationPlugin -> {
                                plugin.process(currentEvent)
                            }
                            is EventPlugin -> {
                                when (currentEvent) {
                                    is IdentifyEvent -> plugin.identify(currentEvent)
                                    is GroupIdentifyEvent -> plugin.groupIdentify(currentEvent)
                                    is RevenueEvent -> plugin.revenue(currentEvent)
                                    else -> plugin.track(currentEvent)
                                }
                            }
                            else -> {
                                plugin.execute(currentEvent)
                                (plugin as? Plugin)?.execute(currentEvent)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace() //TODO propagate error to Logger and Diagnostics
                        currentEvent
                    }
                }
            }
        }

    fun applyClosure(closure: (UniversalPlugin) -> Unit) {
        plugins.forEach {
            closure(it)
        }
    }

    /**
     * Only visible for testing
     */
    internal fun size() = plugins.size
}
