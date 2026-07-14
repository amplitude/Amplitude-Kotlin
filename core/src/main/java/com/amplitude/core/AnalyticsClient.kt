package com.amplitude.core

/**
 * Live access to the host's analytics state, handed to a
 * [com.amplitude.core.platform.UniversalPlugin] at setup. Lets a plugin read the current
 * identity, session, and opt-out state and track events without depending on a concrete host.
 *
 * @property identity the current user and device identity.
 * @property sessionId the current session id.
 * @property optOut whether event tracking is currently opted out.
 */
interface AnalyticsClient {
    val identity: AnalyticsIdentity
    val sessionId: Long
    val optOut: Boolean

    /** Tracks an event with the given type and optional properties. */
    fun trackEvent(
        eventType: String,
        eventProperties: Map<String, Any?>?,
    )
}
