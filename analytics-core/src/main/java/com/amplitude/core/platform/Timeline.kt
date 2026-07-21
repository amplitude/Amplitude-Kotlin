package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import java.util.concurrent.ConcurrentHashMap

open class Timeline {
    internal val plugins: Map<Plugin.Type, Mediator> =
        mapOf(
            Plugin.Type.Before to Mediator(),
            Plugin.Type.Enrichment to Mediator(),
            Plugin.Type.Destination to Mediator(),
            Plugin.Type.Utility to Mediator(),
            Plugin.Type.Observe to Mediator(),
        )
    private val reservedNames = ConcurrentHashMap<String, UniversalPlugin>()
    lateinit var amplitude: Amplitude

    open fun process(incomingEvent: BaseEvent) {
        // Note for future reference:
        // Checking for opt out within the timeline processing since events can be added to the
        // timeline from various sources. For example, the session start and end events are fired
        // from within the timeline.
        if (amplitude.optOut) {
            return
        }

        val beforeResult = applyPlugins(Plugin.Type.Before, incomingEvent)
        val enrichmentResult = applyPlugins(Plugin.Type.Enrichment, beforeResult)

        applyPlugins(Plugin.Type.Destination, enrichmentResult)
    }

    internal fun plugin(name: String): UniversalPlugin? = reservedNames[name]

    fun add(plugin: UniversalPlugin) {
        val name = plugin.name
        if (name != null && reservedNames.putIfAbsent(name, plugin) != null) {
            amplitude.logger.warn("Plugin \"$name\" is already registered; keeping the existing one.")
            return
        }
        try {
            if (plugin is Plugin) {
                plugin.setup(amplitude)
            } else {
                plugin.setup(amplitude.analyticsClient, amplitude.amplitudeContext)
            }
            val type = (plugin as? Plugin)?.type ?: Plugin.Type.Enrichment
            plugins[type]?.add(plugin)
        } catch (t: Throwable) {
            if (name != null) reservedNames.remove(name, plugin)
            throw t
        }
    }

    fun applyPlugins(
        type: Plugin.Type,
        event: BaseEvent?,
    ): BaseEvent? {
        var result: BaseEvent? = event
        val mediator = plugins[type]
        result = applyPlugins(mediator, result)
        return result
    }

    private fun applyPlugins(
        mediator: Mediator?,
        event: BaseEvent?,
    ): BaseEvent? {
        var result: BaseEvent? = event
        result?.let { e ->
            result = mediator?.execute(e)
        }
        return result
    }

    fun remove(plugin: UniversalPlugin) {
        plugin.name?.let { reservedNames.remove(it, plugin) }
        plugins.forEach { (_, mediator) ->
            if (mediator.remove(plugin)) plugin.teardown()
        }
    }

    internal fun pluginsSnapshot(): List<UniversalPlugin> = plugins.values.flatMap { it.snapshot() }

    /**
     * Tears down every registered plugin. Each plugin's [Plugin.teardown] is isolated so one
     * throwing doesn't prevent the others from being cleaned up.
     */
    open fun stop() {
        pluginsSnapshot().forEach { plugin ->
            try {
                plugin.teardown()
            } catch (e: Exception) {
                amplitude.logger.warn("Plugin '${plugin.name ?: plugin::class.java.name}' threw during teardown: $e")
            }
        }
    }

    inline fun <reified T : Plugin> findPlugin(): T? {
        var match: T? = null
        applyClosure { plugin ->
            if (match == null && plugin is T) {
                match = plugin
            }
        }
        return match
    }

    // Applies a closure on all registered plugins
    fun applyClosure(closure: (Plugin) -> Unit) {
        plugins.forEach { (_, mediator) ->
            mediator.applyClosure { if (it is Plugin) closure(it) }
        }
    }
}
