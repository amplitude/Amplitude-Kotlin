package com.amplitude.core.events

/**
 * Minimal, host-agnostic view of an analytics event that cross-host plugins operate on.
 */
interface AnalyticsEvent {
    var userId: String?
    var deviceId: String?
    var timestamp: Long?
    var sessionId: Long?
    var eventType: String
    var eventProperties: MutableMap<String, Any?>?
}
