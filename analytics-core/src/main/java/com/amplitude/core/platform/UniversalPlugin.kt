package com.amplitude.core.platform

import com.amplitude.core.AnalyticsClient
import com.amplitude.core.AnalyticsIdentity
import com.amplitude.core.events.BaseEvent

/**
 * A host-agnostic plugin contract that has no dependency on [com.amplitude.core.Amplitude].
 *
 * Blade SDKs (Experiment, G&S) and third-party analytics hosts (Segment, mParticle) can
 * implement this interface while depending only on `:core`, without pulling in the full
 * Android SDK.
 *
 * [Plugin] extends [UniversalPlugin] for backward compatibility — all existing plugins
 * continue to work without modification.
 *
 * ## Registration model
 *
 * **Amplitude host:** register plugins via [com.amplitude.core.Amplitude.add]. [Plugin]
 * extends [UniversalPlugin], so any existing [Plugin] continues to work without change.
 * Amplitude calls [setup], [execute], and the state-change callbacks automatically as part
 * of its internal lifecycle.
 *
 * **Third-party hosts (Segment, mParticle, etc.):** implement [AnalyticsClient] and manage
 * the [UniversalPlugin] lifecycle yourself — call [setup] with the client reference when the
 * plugin is registered, forward events through [execute], forward state changes to the
 * appropriate callbacks, and call [teardown] when the plugin is removed. No
 * [com.amplitude.core.Amplitude] instance is required.
 */
interface UniversalPlugin {
    /**
     * Optional unique identifier for this plugin, used for deduplication. Defaults to `null`.
     * Plugins with a non-null name are deduplicated on [com.amplitude.core.Amplitude.add].
     */
    val name: String? get() = null

    /**
     * Called when the plugin is registered with an analytics host. Use this to store a
     * reference to [client] and perform one-time setup.
     */
    fun setup(client: AnalyticsClient) {}

    /**
     * Process an event passing through the pipeline. Return the (possibly mutated) event to
     * allow it to continue, or `null` to drop it.
     */
    fun execute(event: BaseEvent): BaseEvent? = event

    /** Release any resources acquired in [setup]. */
    fun teardown() {}

    /** Invoked when the host's userId or deviceId changes. */
    fun onIdentityChanged(identity: AnalyticsIdentity) {}

    /** Invoked when the host's session id changes. */
    fun onSessionIdChanged(sessionId: Long) {}

    /** Invoked when the host's opt-out flag flips. */
    fun onOptOutChanged(optOut: Boolean) {}

    /** Invoked when the host is reset (userId cleared, deviceId rotated). */
    fun onReset() {}
}
