package com.amplitude.android.streaming.internal.network

internal sealed interface DelayedEventsResult {
    data object Success : DelayedEventsResult

    data object RateLimited : DelayedEventsResult

    data class Failure(
        val statusCode: Int?,
        val message: String?,
    ) : DelayedEventsResult
}
