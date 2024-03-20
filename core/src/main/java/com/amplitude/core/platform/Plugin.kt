package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent

interface Plugin {
    enum class Type {
        Before,
        Enrichment,
        Destination,
        Utility,
        Observe
    }

    val type: Type
    var amplitude: Amplitude

    fun setup(amplitude: Amplitude) {
        this.amplitude = amplitude
    }

    fun execute(event: BaseEvent): BaseEvent? {
        return event
    }

    fun teardown() {
        // Clean up any resources from setup if necessary
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

abstract class DestinationPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Destination
    private val timeline: Timeline = Timeline()
    override lateinit var amplitude: Amplitude
    internal var enabled = true

    override fun setup(amplitude: Amplitude) {
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

abstract class ObservePlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Observe

    // ObservePlugin doesn't use the amplitude instance
    // Override it here so it's not required by subclasses
    // This will still be set in Plugin.setup()
    override lateinit var amplitude: Amplitude

    /**
     * Called whenever the User Id changes
     */
    open fun onUserIdChanged(userId: String?) {}

    /**
     * Called whenever the Device Id changes
     */
    open fun onDeviceIdChanged(deviceId: String?) {}

    /**
     * Called whenever the Session Id changes
     */
    open fun onSessionIdChanged(sessionId: Long?) {}

    final override fun execute(event: BaseEvent): BaseEvent? {
        return null
    }
}
