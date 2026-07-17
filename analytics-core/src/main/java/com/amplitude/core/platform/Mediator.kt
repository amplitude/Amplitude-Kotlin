package com.amplitude.core.platform

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import java.util.concurrent.CopyOnWriteArrayList

internal class Mediator(
    private val plugins: CopyOnWriteArrayList<UniversalPlugin> = CopyOnWriteArrayList(),
) {
    fun add(plugin: UniversalPlugin) {
        plugins.add(plugin)
    }

    fun remove(plugin: UniversalPlugin) = plugins.removeAll { it === plugin }

    // Iterator-based copy: Iterable.toList() uses elementAt() for size==1, which races with
    // concurrent remove() on CopyOnWriteArrayList (ArrayIndexOutOfBoundsException).
    fun snapshot(): List<UniversalPlugin> = plugins.toMutableList()

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
                    is Plugin -> {
                        // Binds to Plugin.execute(BaseEvent), which may return a replacement event.
                        result = plugin.execute(result as BaseEvent)
                    }
                    else -> {
                        // Bare UniversalPlugin: the generic hook may mutate or drop, not replace.
                        result = plugin.execute(result as BaseEvent)
                    }
                }
            }
        }
        return result
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
