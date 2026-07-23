package com.amplitude.core.platform

import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.AnalyticsIdentity
import com.amplitude.core.events.AnalyticsEvent

/**
 * A plugin contract that depends only on `:analytics-core`, not on the full
 * [com.amplitude.core.Amplitude] client.
 *
 * Implement this to observe and transform events and react to identity, session, and opt-out
 * changes without pulling in the Android SDK. Most integrations extend [Plugin] instead, which
 * adds Amplitude-specific hooks; every [Plugin] is also a [UniversalPlugin].
 *
 * When hosted by Amplitude, register with [com.amplitude.core.Amplitude.add] and the host
 * drives the lifecycle for you. When hosting the plugin yourself, call [setup] on registration,
 * forward events through [execute] and state changes to the callbacks, and call [teardown] on
 * removal.
 */
public interface UniversalPlugin {
    /**
     * Optional unique identifier used to deduplicate plugins. When non-null, registering a
     * second plugin with the same name is ignored.
     */
    public val name: String? get() = null

    /**
     * Called when the plugin is registered with a host. [client] exposes live identity,
     * session, and opt-out state; [context] carries shared configuration such as the API key,
     * server zone, and logger.
     */
    public fun setup(
        client: AnalyticsClient,
        context: AmplitudeContext,
    ) {}

    /**
     * Processes an event as it flows through the pipeline. Plugins may read and mutate the
     * event's fields and may drop it by returning `null`, but must return the same event
     * instance (not a different concrete type).
     */
    public fun <T : AnalyticsEvent> execute(event: T): T? = event

    /** Releases any resources acquired in [setup]. */
    public fun teardown() {}

    /** Called when the user id or device id changes. */
    public fun onIdentityChanged(identity: AnalyticsIdentity) {}

    /** Called when the session id changes. */
    public fun onSessionIdChanged(sessionId: Long) {}

    /** Called when the opt-out setting changes. */
    public fun onOptOutChanged(optOut: Boolean) {}

    /** Called when the host is reset: the user id is cleared and the device id is regenerated. */
    public fun onReset() {}
}
