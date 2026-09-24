package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.common.Logger

private const val STREAM_SESSION_ID = "stream_session_id"

/**
 * One hour.
 *
 * Server TTL for delayed events. Matches iOS and web (`ttlMs` / `DEFAULT_HEARTBEAT_DELAY_TIMEOUT`).
 * Mobile keeps overwriting the queued payload locally between pulses.
 */
internal const val DELAYED_EVENT_TIMEOUT_MILLIS = 60 * 60 * 1_000L

internal val StreamingDiGraph.storagePipeline: StoragePipeline by weak {
    StoragePipeline(
        queue = delayedEventsQueue,
        logger = logger,
    )
}

/**
 * Turns delayed events into queued HTTP requests keyed by stream session.
 */
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
