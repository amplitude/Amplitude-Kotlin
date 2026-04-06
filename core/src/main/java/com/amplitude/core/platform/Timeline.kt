package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent

open class Timeline {
    @PublishedApi
    internal val plugins: Map<Plugin.Type, Mediator> =
        mapOf(
            Plugin.Type.Before to Mediator(),
            Plugin.Type.Enrichment to Mediator(),
            Plugin.Type.Destination to Mediator(),
            Plugin.Type.Utility to Mediator(),
        )
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

    fun add(plugin: Plugin) {
        plugin.setup(amplitude)
        plugins[plugin.type]?.add(plugin)
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

    fun remove(plugin: Plugin) {
        // remove all plugins with this name in every category
        plugins.forEach { (_, list) ->
            val wasRemoved = list.remove(plugin)
            if (wasRemoved) {
                plugin.teardown()
            }
        }
    }

    /**
     * Remove any existing plugin whose [Plugin.name] matches the given [name].
     * Used for plugin deduplication when adding a new plugin with a non-null name.
     */
    fun removeByName(name: String) {
        plugins.forEach { (_, mediator) ->
            mediator.removeByName(name)
        }
    }

    /**
     * Find a plugin by its type.
     *
     * @return the plugin instance if found, null otherwise
     */
    inline fun <reified T : Plugin> findPlugin(): T? =
        plugins.values
            .flatMap { it.plugins }
            .filterIsInstance<T>()
            .firstOrNull()

    /**
     * Find a plugin by its [Plugin.name].
     *
     * @param name the name of the plugin
     * @return the plugin instance if found, null otherwise
     */
    fun findPluginByName(name: String): Plugin? =
        plugins.values
            .flatMap { it.plugins }
            .firstOrNull { it.name == name }

    // Applies a closure on all registered plugins
    fun applyClosure(closure: (Plugin) -> Unit) {
        plugins.forEach { (_, mediator) ->
            mediator.applyClosure(closure)
        }
    }
}
