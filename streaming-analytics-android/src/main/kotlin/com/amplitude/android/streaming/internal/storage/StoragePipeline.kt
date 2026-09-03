package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.common.Logger

private const val STREAM_SESSION_ID = "stream_session_id"

/**
 * Five minutes.
 *
 * Server TTL for delayed events. Mobile can keep overwriting the queued payload locally,
 * which reduces heartbeat traffic.
 */
internal const val DELAYED_EVENT_TIMEOUT_MILLIS = 5 * 60 * 1_000L

internal val StreamingDiGraph.storagePipeline: StoragePipeline by weak {
    StoragePipeline(
        queue = delayedEventsQueue,
        logger = logger,
    )
}

internal class StoragePipeline(
    private val queue: DelayedEventsQueue,
    private val logger: Logger,
) {
    suspend fun onDelayedEvent(event: DelayedEvent) {
        val streamSessionId = event.eventProperties?.get(STREAM_SESSION_ID) as? String
        if (streamSessionId.isNullOrBlank()) {
            logger.error("Dropping delayed event without $STREAM_SESSION_ID")
            return
        }
        val entity = event.toEntity()
        queue.enqueue(
            DelayedEventsRequestEntity(
                id = streamSessionId,
                timeoutMillis = DELAYED_EVENT_TIMEOUT_MILLIS,
                events =
                    if (event.kind == DelayedEvent.Kind.DELAYED) {
                        listOf(entity)
                    } else {
                        emptyList()
                    },
                instantEvents =
                    if (event.kind == DelayedEvent.Kind.INSTANT) {
                        listOf(entity)
                    } else {
                        null
                    },
            ),
        )
    }
}
