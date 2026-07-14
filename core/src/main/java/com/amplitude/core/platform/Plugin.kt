package com.amplitude.core.platform

import com.amplitude.core.Amplitude
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
     * Optional stable plugin identifier. When non-null, adding another plugin
     * with the same name is **skipped** — the first registration wins and is
     * not torn down. A warning is logged for the duplicate.
     */
    override val name: String? get() = null

    /**
     * Called by [Amplitude.add] to wire this plugin into the host instance.
     * The default implementation of [UniversalPlugin.setup] is a no-op for
     * [Plugin] subclasses — callers always go through this typed overload.
     */
    fun setup(amplitude: Amplitude) {
        this.amplitude = amplitude
    }

    override fun setup(client: AnalyticsClient) {
        (client as? Amplitude)?.let { setup(it) }
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        return event
    }

    override fun teardown() {
        // Clean up any resources from setup if necessary
    }

    /**
     * State-change callbacks. Default no-ops so existing plugins are unaffected.
     * Delivered to every registered plugin (timeline and observe store) by
     * [com.amplitude.core.Amplitude]; each invocation is isolated so one plugin throwing an
     * exception does not affect the others.
     *
     * Threading: callbacks are delivered **synchronously on the thread that triggers the
     * change** — e.g. the caller's thread for [com.amplitude.core.Amplitude.setUserId] /
     * [com.amplitude.core.Amplitude.setDeviceId], and a background (session/lifecycle) thread
     * for [onSessionIdChanged]. No specific thread is guaranteed (in particular, not the main
     * thread). Keep callbacks fast and non-blocking; identity values are read from `@Volatile`
     * fields so individual reads are safe, but do not assume a stable thread across callbacks.
     */
    fun onUserIdChanged(userId: String?) {}

    fun onDeviceIdChanged(deviceId: String?) {}

    /**
     * Invoked when the session id changes on the Amplitude instance this plugin
     * is registered with. Only fires for SDK builds that maintain a session id
     * (e.g. the Android SDK).
     */
    override fun onSessionIdChanged(sessionId: Long) {}

    /**
     * Invoked when the [Amplitude.optOut] flag flips on the Amplitude instance
     * this plugin is registered with.
     */
    override fun onOptOutChanged(optOut: Boolean) {}

    /**
     * Invoked when [Amplitude.reset] is called on the Amplitude instance this
     * plugin is registered with. Delivered after [onUserIdChanged], [onDeviceIdChanged],
     * and [onIdentityChanged] for that reset.
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
