package com.amplitude.core.platform

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import java.util.concurrent.CopyOnWriteArrayList

@PublishedApi
internal class Mediator(
    @PublishedApi
    internal val plugins: CopyOnWriteArrayList<Plugin> = CopyOnWriteArrayList(),
) {
    fun add(plugin: Plugin) {
        plugins.add(plugin)
    }

    fun remove(plugin: Plugin) = plugins.removeAll { it === plugin }

    /**
     * Remove any plugin whose [Plugin.name] matches the given [name], calling [Plugin.teardown] on each.
     */
    fun removeByName(name: String) {
        val iterator = plugins.iterator()
        while (iterator.hasNext()) {
            val plugin = iterator.next()
            if (plugin.name == name) {
                plugins.remove(plugin)
                plugin.teardown()
            }
        }
    }

    fun execute(event: BaseEvent): BaseEvent? {
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

    fun applyClosure(closure: (Plugin) -> Unit) {
        plugins.forEach {
            closure(it)
        }
    }

    /**
     * Only visible for testing
     */
    internal fun size() = plugins.size
}
