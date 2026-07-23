package com.amplitude.core.utilities.http

public interface HttpClientInterface {
    public fun upload(
        events: String,
        diagnostics: String? = null,
    ): AnalyticsResponse
}
