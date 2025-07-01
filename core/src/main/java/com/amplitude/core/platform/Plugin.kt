package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.plugins.UniversalPlugin

interface Plugin : UniversalPlugin {
    override val name: String?
        get() = this::class.simpleName

    enum class Type {
        Before,
        Enrichment,
        Destination,
        Utility,
        Observe,
    }

    val type: Type
    var amplitude: Amplitude

    fun setup(amplitude: Amplitude) {
        this.amplitude = amplitude
    }

    fun execute(event: BaseEvent): BaseEvent? {
        return event
    }

    override fun teardown() {
        // Clean up any resources from setup if necessary
    }
}

interface EventPlugin : Plugin {
    fun track(payload: BaseEvent): BaseEvent? {
        return execute(payload)
    }

    fun identify(payload: IdentifyEvent): IdentifyEvent? {
        return payload
    }

    fun groupIdentify(payload: GroupIdentifyEvent): GroupIdentifyEvent? {
        return payload
    }

    fun revenue(payload: RevenueEvent): RevenueEvent? {
        return payload
    }

    open fun flush() {}
}

abstract class DestinationPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Destination
    override lateinit var amplitude: Amplitude
    internal lateinit var timeline: Timeline
    internal var enabled = true

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        timeline = Timeline(amplitude)
    }

    fun add(plugin: Plugin) {
        plugin.setup(amplitude)
        timeline.add(plugin)
    }

    fun remove(plugin: Plugin) {
        timeline.remove(plugin)
    }

    open fun process(event: BaseEvent): BaseEvent? {
        // Skip this destination if it is disabled via settings
        if (!enabled) {
            return null
        }
        val beforeResult = timeline.applyPlugins(Plugin.Type.Before, event) ?: return null
        val enrichmentResult = timeline.applyPlugins(Plugin.Type.Enrichment, beforeResult) ?: return null

        val destinationResult =
            when (enrichmentResult) {
                is IdentifyEvent -> {
                    identify(enrichmentResult)
                }
                is GroupIdentifyEvent -> {
                    groupIdentify(enrichmentResult)
                }
                is RevenueEvent -> {
                    revenue(enrichmentResult)
                }
                else -> {
                    track(enrichmentResult)
                }
            }

        return destinationResult
    }

    final override fun execute(event: BaseEvent): BaseEvent? {
        return null
    }
}

abstract class ObservePlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Observe
}
