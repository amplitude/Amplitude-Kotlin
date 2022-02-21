package com.amplitude.platform

import com.amplitude.Amplitude
import com.amplitude.events.BaseEvent
import com.amplitude.events.GroupIdentifyEvent
import com.amplitude.events.IdentifyEvent
import com.amplitude.events.RevenueEvent

interface Plugin {
    enum class Type {
        Before,
        Enrichment,
        Destination,
        Utility,
        Observe
    }

    val type: Type
    var amplitude: com.amplitude.Amplitude

    fun setup(amplitude: com.amplitude.Amplitude) {
        this.amplitude = amplitude
    }

    fun execute(event: BaseEvent): BaseEvent? {
        return event
    }
}

interface EventPlugin : Plugin {
    fun track(payload: BaseEvent): BaseEvent? {
        return payload
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

abstract class DestinationPlugin: EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Destination
    private val timeline: Timeline = Timeline()
    override lateinit var amplitude: com.amplitude.Amplitude
    internal var enabled = true

    override fun setup(amplitude: com.amplitude.Amplitude) {
        super.setup(amplitude)
        timeline.amplitude = amplitude
    }

    fun add(plugin: Plugin) {
        plugin.amplitude = this.amplitude
        timeline.add(plugin)
    }

    fun remove(plugin: Plugin) {
        timeline.remove(plugin)
    }

    fun process(event: BaseEvent?): BaseEvent? {
        // Skip this destination if it is disabled via settings
        if (!enabled) {
            return null
        }
        val beforeResult = timeline.applyPlugins(Plugin.Type.Before, event)
        val enrichmentResult = timeline.applyPlugins(Plugin.Type.Enrichment, beforeResult)

        val destinationResult = enrichmentResult?.let {
            when (it) {
                is IdentifyEvent -> {
                    identify(it)
                }
                is GroupIdentifyEvent -> {
                    groupIdentify(it)
                }
                is RevenueEvent -> {
                    revenue(it)
                }
                else -> {
                    track(it)
                }
            }
        }

        return destinationResult
    }

    final override fun execute(event: BaseEvent): BaseEvent? {
        return null
    }
}

