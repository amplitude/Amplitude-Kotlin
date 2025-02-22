package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent

open class Timeline {
    internal val plugins: Map<Plugin.Type, Mediator> = mapOf(
        Plugin.Type.Before to Mediator(mutableListOf()),
        Plugin.Type.Enrichment to Mediator(mutableListOf()),
        Plugin.Type.Destination to Mediator(mutableListOf()),
        Plugin.Type.Utility to Mediator(mutableListOf())
    )
    lateinit var amplitude: Amplitude

    open fun process(incomingEvent: BaseEvent) {
        // Note for future reference:
        // Checking for opt out within the timeline processing since events can be added to the
        // timeline from various sources. For example, the session start and end events are fired
        // from within the timeline.
        if (amplitude.configuration.optOut) {
            return
        }

        val beforeResult = applyPlugins(Plugin.Type.Before, incomingEvent)
        val enrichmentResult = applyPlugins(Plugin.Type.Enrichment, beforeResult)

        applyPlugins(Plugin.Type.Destination, enrichmentResult)
    }

    fun add(plugin: Plugin) {
        plugin.setup(amplitude)
        plugins[plugin.type]?.add(plugin)
    }

    fun applyPlugins(type: Plugin.Type, event: BaseEvent?): BaseEvent? {
        var result: BaseEvent? = event
        val mediator = plugins[type]
        result = applyPlugins(mediator, result)
        return result
    }

    private fun applyPlugins(mediator: Mediator?, event: BaseEvent?): BaseEvent? {
        var result: BaseEvent? = event
        result?.let { e ->
            result = mediator?.execute(e)
        }
        return result
    }

    fun remove(plugin: Plugin) {
        // remove all plugins with this name in every category
        plugins.forEach { (_, list) ->
            val wasRemoved = list.remove(plugin)
            if (wasRemoved) {
                plugin.teardown()
            }
        }
    }

    // Applies a closure on all registered plugins
    fun applyClosure(closure: (Plugin) -> Unit) {
        plugins.forEach { (_, mediator) ->
            mediator.applyClosure(closure)
        }
    }
}
