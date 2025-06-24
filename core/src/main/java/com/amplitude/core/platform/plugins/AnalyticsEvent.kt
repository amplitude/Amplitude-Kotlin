package com.amplitude.core.platform.plugins

interface AnalyticsEvent {
    val userId: String?
    val deviceId: String?
    val sessionId: Long?
    val timestamp: Long?
    val eventType: String
    var eventProperties: MutableMap<String, Any>?
}
