package com.amplitude.core.events

/**
 * Minimal, host-agnostic view of an analytics event that cross-host plugins operate on.
 */
public interface AnalyticsEvent {
    public var userId: String?
    public var deviceId: String?
    public var timestamp: Long?
    public var sessionId: Long?
    public var eventType: String
    public var eventProperties: MutableMap<String, Any?>?
}
