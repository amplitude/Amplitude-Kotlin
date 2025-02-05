package com.amplitude.core.utilities.http

interface HttpClientInterface {
    fun upload(events: String, diagnostics: String? = null): AnalyticsResponse
}