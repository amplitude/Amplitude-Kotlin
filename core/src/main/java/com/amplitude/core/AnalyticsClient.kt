package com.amplitude.core

interface AnalyticsClient {
    val identity: AnalyticsIdentity
    val sessionId: Long
    val optOut: Boolean

    fun track(
        eventType: String,
        eventProperties: Map<String, Any?>? = null,
    )
}
