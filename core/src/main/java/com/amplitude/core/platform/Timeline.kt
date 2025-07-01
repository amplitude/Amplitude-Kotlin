package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.plugins.UniversalPlugin

const val DEFAULT_SESSION_ID = -1L

open class Timeline(
    val amplitude: Amplitude
) {
    internal val plugins: Map<Plugin.Type, Mediator> =
        Plugin.Type.entries.associateWith { Mediator() }.toMutableMap()
    internal val pluginsByName: MutableMap<String, UniversalPlugin> = mutableMapOf()

    open var sessionId: Long = DEFAULT_SESSION_ID

    open fun start() {
        // This method can be overridden by subclasses to perform any necessary initialization.
        // For example, it can be used to set up the initial session ID or load any required data.
    }

    open fun process(incomingEvent: BaseEvent) {
        // Note for future reference:
        // Checking for opt out within the timeline processing since events can be added to the
        // timeline from various sources. For example, the session start and end events are fired
        // from within the timeline.
        if (amplitude.configuration.optOut) {
            return
        }

        val beforeResult = applyPlugins(Plugin.Type.Before, incomingEvent) ?: return
        val enrichmentResult = applyPlugins(Plugin.Type.Enrichment, beforeResult) ?: return
        
        applyPlugins(Plugin.Type.Destination, enrichmentResult)
    }

    open fun stop() {
        // This method can be overridden by subclasses to perform any necessary cleanup.
        // For example, it can be used to stop any ongoing processes or release resources.
    }

    fun add(plugin: UniversalPlugin) {
        plugin.setup(
            analyticsClient = amplitude,
            amplitudeContext = amplitude.amplitudeContext
        )
        if (plugin is Plugin) {
            plugin.setup(amplitude)
        }
        plugin.name?.let { name ->
            if (pluginsByName.contains(name)) return
            pluginsByName[name] = plugin
        }
        val pluginType = (plugin as? Plugin)?.type ?: Plugin.Type.Enrichment
        plugins[pluginType]?.add(plugin)
    }

    fun applyPlugins(type: Plugin.Type, event: BaseEvent): BaseEvent? {
        return plugins[type]?.execute(event)
    }

    fun remove(plugin: UniversalPlugin) {
        // remove all plugins with this name in every category
        plugins.forEach { (_, list) ->
            val wasRemoved = list.remove(plugin)
            if (wasRemoved) {
                plugin.teardown()
            }
        }
        plugin.name?.let { name ->
            pluginsByName.remove(name)
        }
    }

    // Applies a closure on all registered plugins
    fun applyClosure(closure: (UniversalPlugin) -> Unit) {
        plugins.forEach { (_, mediator) ->
            mediator.applyClosure(closure)
        }
    }

    fun getPluginsByType(type: Plugin.Type): List<UniversalPlugin> {
        return plugins[type]?.plugins ?: emptyList()
    }
}
