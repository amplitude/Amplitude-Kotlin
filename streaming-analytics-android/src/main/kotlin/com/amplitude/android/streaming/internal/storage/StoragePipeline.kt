package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.StreamEvents
import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.singleton
import com.amplitude.common.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val STREAM_SESSION_ID = "[Streaming] Stream Session ID"
private const val PERSISTENCE_MAX_ATTEMPTS = 2

/**
 * One hour.
 *
 * Server TTL for delayed events. Matches iOS and web (`ttlMs` / `DEFAULT_HEARTBEAT_DELAY_TIMEOUT`).
 * Mobile keeps overwriting the queued payload locally between pulses.
 */
internal const val DELAYED_EVENT_TIMEOUT_MILLIS = 60 * 60 * 1_000L

internal val StreamingDiGraph.storagePipeline: StoragePipeline by singleton {
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
    private val mutex = Mutex()

    // Keep writes ordered across the retry so an older heartbeat cannot follow a stop.
    suspend fun onDelayedEvents(events: List<DelayedEvent>) = mutex.withLock {
        events
            .groupBy { event ->
                event.eventProperties?.get(STREAM_SESSION_ID) as? String
            }
            .forEach { (streamSessionId, events) ->
                if (streamSessionId.isNullOrBlank()) {
                    logger.error("Dropping delayed event without $STREAM_SESSION_ID")
                    return@forEach
                }
                queueEvents(streamSessionId, events)
            }
    }

    private suspend fun queueEvents(streamSessionId: String, events: List<DelayedEvent>) {
        val delayedEventEntities = events
            .filter { it.kind == DelayedEvent.Kind.DELAYED }
            .map { it.toEntity() }

        val instantEventEntities = events
            .filter { it.kind == DelayedEvent.Kind.INSTANT }
            .map { it.toEntity() }

        val isCleanStop = events.any {
            it.kind == DelayedEvent.Kind.INSTANT && it.eventType == StreamEvents.STREAM_STOPPED
        }
        enqueueWithRetry(
            DelayedEventsRequestEntity(
                id = streamSessionId,
                // A zero timeout sends the clean stop and deletes the server's delayed snapshot.
                timeoutMillis = if (isCleanStop) 0L else DELAYED_EVENT_TIMEOUT_MILLIS,
                events = delayedEventEntities,
                instantEvents = instantEventEntities,
            ),
        )
    }

    private suspend fun enqueueWithRetry(request: DelayedEventsRequestEntity) {
        repeat(PERSISTENCE_MAX_ATTEMPTS) {
            try {
                queue.enqueue(request)
                return
            } catch (_: QueueUnavailableException) {
                // Retry the local queue operation immediately.
            }
        }
        logger.error("Failed to persist delayed-events update ${request.id} after $PERSISTENCE_MAX_ATTEMPTS attempts; dropping update")
    }
}
