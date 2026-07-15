package com.amplitude.core.platform

import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent

interface Plugin : UniversalPlugin {
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
     * Optional stable identifier. When non-null, registering another plugin with the same
     * name is ignored — the first one wins.
     */
    override val name: String? get() = null

    /**
     * Called when the plugin is registered with an [Amplitude] instance. Override to
     * initialize the plugin, and call `super.setup(amplitude)` to keep the default wiring.
     */
    fun setup(amplitude: Amplitude) {
        this.amplitude = amplitude
        setup(amplitude.analyticsClient, amplitude.amplitudeContext)
    }

    /** Not used by [Plugin] implementations; they receive [setup] with the [Amplitude] instance instead. */
    override fun setup(
        client: AnalyticsClient,
        context: AmplitudeContext,
    ) {}

    override fun execute(event: BaseEvent): BaseEvent? {
        return event
    }

    override fun teardown() {
        // Clean up any resources from setup if necessary
    }

    /**
     * Called when the user id or device id changes.
     *
     * Callbacks run synchronously on the thread that triggered the change, which is not
     * guaranteed to be the main thread, so keep them fast and non-blocking. An exception
     * thrown by one plugin does not prevent the others from being notified.
     */
    fun onUserIdChanged(userId: String?) {}

    /** Called when the device id changes. Shares the threading contract of [onUserIdChanged]. */
    fun onDeviceIdChanged(deviceId: String?) {}

    /** Called when the session id changes. Only fires on SDK builds that track sessions. */
    override fun onSessionIdChanged(sessionId: Long) {}

    /** Called when the [Amplitude.optOut] setting changes. */
    override fun onOptOutChanged(optOut: Boolean) {}

    /**
     * Called when [Amplitude.reset] is invoked, after the [onUserIdChanged],
     * [onDeviceIdChanged], and [onIdentityChanged] callbacks for that reset.
     */
    override fun onReset() {}
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
