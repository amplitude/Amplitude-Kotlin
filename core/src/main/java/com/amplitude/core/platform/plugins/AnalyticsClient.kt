package com.amplitude.core.platform.plugins

interface AnalyticsClient<I : AnalyticsIdentity> {
    val identity: I
    val sessionId: Long
    val optOut: Boolean

    fun track(eventType: String, eventProperties: Map<String, Any?>? = null)
}