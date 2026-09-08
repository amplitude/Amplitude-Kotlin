package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.network.DelayedEventsRequestDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
internal data class DelayedEventsRequestEntity(
    val id: String,
    val timeoutMillis: Long,
    val events: List<DelayedEventEntity>,
    val instantEvents: List<DelayedEventEntity>? = null,
    /**
     * Filename of this entry in the delayed-events queue (`{sequence}-{sha256(id)}`).
     * Not serialized: identity is the file name, not a field in the payload.
     */
    @Transient val queueKey: String? = null,
)

internal fun DelayedEventsRequestEntity.toDto(): DelayedEventsRequestDto =
    DelayedEventsRequestDto(
        id = id,
        timeoutMillis = timeoutMillis,
        events = events.map { it.toDelayedEvent(DelayedEvent.Kind.DELAYED) },
        instantEvents = instantEvents?.map { it.toDelayedEvent(DelayedEvent.Kind.INSTANT) },
    )

internal fun DelayedEventsRequestDto.toRequest(): DelayedEventsRequestEntity =
    DelayedEventsRequestEntity(
        id = id,
        timeoutMillis = timeoutMillis,
        events = events.map { it.toEntity() },
        instantEvents = instantEvents?.map { it.toEntity() },
    )
