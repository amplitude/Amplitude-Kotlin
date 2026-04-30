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
        Observe,
    }

    val type: Type
    var amplitude: Amplitude

    /**
     * Optional unique identifier for this plugin, used for deduplication.
     *
     * When a plugin with a non-null [name] is added via [Amplitude.add], any
     * previously registered plugin with the same name is removed (and its
     * [teardown] called) before the new plugin is wired in. Plugins that leave
     * [name] as `null` are never deduplicated.
     *
     * Defaults to `null` to preserve binary compatibility with existing plugins.
     */
    val name: String? get() = null

    fun setup(amplitude: Amplitude) {
        this.amplitude = amplitude
    }

    fun execute(event: BaseEvent): BaseEvent? {
        return event
    }

    fun teardown() {
        // Clean up any resources from setup if necessary
    }

    /**
     * Invoked when [Amplitude.setUserId] mutates the userId on the
     * Amplitude instance this plugin is registered with.
     */
    fun onUserIdChanged(userId: String?) {}

    /**
     * Invoked when [Amplitude.setDeviceId] mutates the deviceId on the
     * Amplitude instance this plugin is registered with.
     */
    fun onDeviceIdChanged(deviceId: String?) {}

    /**
     * Invoked when the session id changes on the Amplitude instance this plugin
     * is registered with. Only fires for SDK builds that maintain a session id
     * (e.g. the Android SDK).
     */
    fun onSessionIdChanged(sessionId: Long) {}

    /**
     * Invoked when the [Amplitude.optOut] flag flips on the Amplitude instance
     * this plugin is registered with.
     */
    fun onOptOutChanged(optOut: Boolean) {}

    /**
     * Invoked when [Amplitude.reset] is called on the Amplitude instance this
     * plugin is registered with. The accompanying userId/deviceId changes are
     * delivered via the same notification (one batched callback, not two).
     */
    fun onReset() {}
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

        val destinationResult =
            enrichmentResult?.let {
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

    abstract override fun onUserIdChanged(userId: String?)

    abstract override fun onDeviceIdChanged(deviceId: String?)

    final override fun execute(event: BaseEvent): BaseEvent? {
        return null
    }
}
