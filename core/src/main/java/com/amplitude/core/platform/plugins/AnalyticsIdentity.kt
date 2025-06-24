package com.amplitude.core.platform.plugins

interface AnalyticsIdentity {
    val deviceId: String?
    val userId: String?
    val userProperties: MutableMap<String, Any>?
}