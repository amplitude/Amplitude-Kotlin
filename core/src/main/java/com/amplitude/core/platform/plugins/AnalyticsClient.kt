package com.amplitude.core.platform.plugins

interface AnalyticsClient {
    var identity: AnalyticsIdentity
    var sessionId: Long
    var optOut: Boolean

    fun track(
        eventType: String,
        eventProperties: Map<String, Any>?,
    ) {}
}
