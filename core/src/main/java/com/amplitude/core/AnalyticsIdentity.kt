package com.amplitude.core

interface AnalyticsIdentity {
    val userId: String?
    val deviceId: String?
}
