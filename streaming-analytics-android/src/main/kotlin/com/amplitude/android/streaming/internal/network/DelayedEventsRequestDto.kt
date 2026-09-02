package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.DelayedEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class DelayedEventsRequestDto(
    val id: String,
    @SerialName("api_key")
    val apiKey: String? = null,
    @SerialName("timeout")
    val timeoutMillis: Long,
    val events: List<DelayedEvent>,
    @SerialName("instant_events")
    val instantEvents: List<DelayedEvent>? = null,
) {
    fun toJson(apiKey: String): String = delayedEventsJson.encodeToString(copy(apiKey = apiKey))
}

private val delayedEventsJson =
    Json {
        encodeDefaults = false
        explicitNulls = false
    }


