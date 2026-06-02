package com.amplitude.core

interface AnalyticsClient {
    val identity: AnalyticsIdentity
    val sessionId: Long
    val optOut: Boolean

    fun trackEvent(
        eventType: String,
        eventProperties: Map<String, Any?>?,
    )
}
