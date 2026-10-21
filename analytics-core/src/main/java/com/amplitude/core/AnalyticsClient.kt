package com.amplitude.core

/**
 * Live access to the host's analytics state, handed to a
 * [com.amplitude.core.platform.UniversalPlugin] at setup. Lets a plugin read the current
 * identity, session, and opt-out state and track events without depending on a concrete host.
 *
 * @property identity the current identity. Hosts must keep its user properties current; plugins
 * treat an empty map as a user without properties.
 * @property sessionId the current session id.
 * @property optOut whether event tracking is currently opted out.
 */
public interface AnalyticsClient {
    public val identity: AnalyticsIdentity

    public val sessionId: Long
    public val optOut: Boolean

    /** Tracks an event with the given type and optional properties. */
    public fun track(
        eventType: String,
        eventProperties: Map<String, Any?>?,
    )
}
