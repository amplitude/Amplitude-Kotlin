package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.storage.DelayedEventsQueue
import com.amplitude.android.streaming.internal.storage.DelayedEventsRequestEntity
import com.amplitude.android.streaming.internal.storage.QueueUnavailableException
import com.amplitude.android.streaming.internal.storage.delayedEventsQueue
import com.amplitude.android.streaming.internal.storage.toDto
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.singleton
import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

internal val StreamingDiGraph.uploadPipeline: UploadPipeline by singleton {
    UploadPipeline(
        scope = scope,
        queue = delayedEventsQueue,
        endpoint = delayedEventsEndpoint,
        logger = logger,
        configuration = configuration,
    )
}

private val DELAYS_IN_MS = listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L)
private const val RATE_LIMIT_MIN_DELAY_MS = 30_000L

/** How often a delayed payload is re-sent to keep the server row alive. Matches iOS pulseInterval. */
internal const val DELAYED_EVENT_PULSE_INTERVAL_MILLIS = 60_000L

/** Heartbeat and flush state is owned by this pipeline's drain worker. */
private class StreamDelivery {
    var heartbeatAtMs = 0L
    var flushedGeneration = 0L
}

private class PendingAcknowledgment(val request: DelayedEventsRequestEntity) {
    var cleanupAtMs = 0L
    var cleanupAttempt = 0
}

/** Access shared request and cleanup state only while holding [mutex]. */
private class QueueDeliveryState {
    val mutex = Mutex()
    var attempt = 0
    var backoffUntilMs = 0L
    val acknowledgments = mutableMapOf<String, PendingAcknowledgment>()
}

/**
 * One scope-owned collector serializes uploads and acknowledgment retries. Triggers coalesce
 * while an upload runs; one timer wakes the collector at the next delivery deadline.
 */
internal class UploadPipeline(
    scope: CoroutineScope,
    private val queue: DelayedEventsQueue,
    private val endpoint: DelayedEventsEndpoint,
    private val logger: Logger,
    private val configuration: Configuration,
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() },
) {
    private val workerUploadTriggers = MutableStateFlow(0L)
    private val flushGeneration = AtomicLong()

    // Null means the worker stopped, so teardown cannot keep waiting for it.
    private val lastDrainedTrigger = MutableStateFlow<Long?>(0L)
    private val streams = mutableMapOf<String, StreamDelivery>()
    private val delivery = deliveries.getOrPut(queue.storageKey) { QueueDeliveryState() }

    init {
        scope.launch {
            var retry: Job? = null
            workerUploadTriggers.collect { trigger ->
                if (trigger == lastDrainedTrigger.value) return@collect
                retry?.cancel()
                val deadline = try {
                    drain()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logger.error("Delayed-events upload failed: ${error.message}")
                    currentTimeMs() + DELAYS_IN_MS.first()
                }
                if (deadline == null && workerUploadTriggers.value == trigger) {
                    lastDrainedTrigger.value = trigger
                }
                retry = deadline?.let {
                    launch {
                        delay((it - currentTimeMs()).coerceAtLeast(1L).milliseconds)
                        triggerUpload()
                    }
                }
            }
        }.invokeOnCompletion { lastDrainedTrigger.value = null }
    }

    fun onNewEvent() {
        triggerUpload()
    }

    /** Requests one fresh upload per queued stream, subject to retry backoff. */
    fun flush() {
        flushGeneration.incrementAndGet()
        triggerUpload()
    }

    /** Awaits delivery and acknowledgment retries, deferring queued uploads while offline. */
    suspend fun flushAndDrain() {
        flushGeneration.incrementAndGet()
        val trigger = triggerUpload()
        lastDrainedTrigger.first { it == null || it >= trigger }
            ?: throw CancellationException("Delayed-events upload worker stopped")
    }

    private fun triggerUpload(): Long = workerUploadTriggers.updateAndGet { it + 1 }

    private suspend fun drain(): Long? {
        while (true) {
            // Read the latest payload only after acquiring ownership, and keep ownership
            // through HTTP completion and acknowledgment. Timer waits happen outside this lock.
            delivery.mutex.withLock {
                retryAcknowledgments()
                val now = currentTimeMs()
                streams.entries.removeAll { (id, state) -> id !in delivery.acknowledgments && state.heartbeatAtMs <= now }
                val generation = flushGeneration.get()
                var nextAtMs: Long? = delivery.acknowledgments.values.minOfOrNull { it.cleanupAtMs }
                if (configuration.offline == true) return nextAtMs
                val request = try {
                    queue.peek { request ->
                        val state = streams.getOrPut(request.id) { StreamDelivery() }
                        if (request.id in delivery.acknowledgments) {
                            false
                        } else {
                            val urgent = !request.instantEvents.isNullOrEmpty() || state.flushedGeneration < generation
                            val dueAt = max(delivery.backoffUntilMs, if (urgent) now else state.heartbeatAtMs)
                            nextAtMs = minOf(nextAtMs ?: dueAt, dueAt)
                            dueAt <= now
                        }
                    }
                } catch (_: QueueUnavailableException) {
                    return currentTimeMs() + DELAYS_IN_MS.first()
                }
                if (request != null) {
                    send(request, generation)
                } else {
                    return nextAtMs
                }
            }
        }
    }

    private suspend fun send(request: DelayedEventsRequestEntity, generation: Long) {
        when (val result = endpoint.send(request.toDto())) {
            DelayedEventsResult.Offline -> Unit
            DelayedEventsResult.Success -> settle(request, generation)
            DelayedEventsResult.RateLimited -> scheduleBackoff(RATE_LIMIT_MIN_DELAY_MS)
            is DelayedEventsResult.FailureNoRetry -> {
                logger.error("Dropping delayed-events request ${request.id}: unrecoverable HTTP ${result.statusCode} ${result.message}")
                settle(request, generation)
            }
            is DelayedEventsResult.Failure -> scheduleBackoff(0L)
        }
    }

    private suspend fun settle(request: DelayedEventsRequestEntity, generation: Long) {
        delivery.attempt = 0
        delivery.backoffUntilMs = 0L
        val state = streams.getValue(request.id)
        state.heartbeatAtMs = currentTimeMs() + DELAYED_EVENT_PULSE_INTERVAL_MILLIS
        state.flushedGeneration = generation
        delivery.acknowledgments[request.id] = PendingAcknowledgment(request)
        retryAcknowledgments()
    }

    private suspend fun retryAcknowledgments() {
        val entries = delivery.acknowledgments.values.iterator()
        while (entries.hasNext()) {
            val state = entries.next()
            if (currentTimeMs() < state.cleanupAtMs) continue
            if (queue.acknowledge(state.request)) {
                entries.remove()
            } else {
                state.cleanupAtMs = currentTimeMs() + DELAYS_IN_MS[state.cleanupAttempt.coerceAtMost(DELAYS_IN_MS.lastIndex)]
                state.cleanupAttempt++
            }
        }
    }

    private fun scheduleBackoff(minDelayMs: Long) {
        val delayMs = max(minDelayMs, DELAYS_IN_MS[delivery.attempt.coerceAtMost(DELAYS_IN_MS.lastIndex)])
        delivery.attempt++
        delivery.backoffUntilMs = currentTimeMs() + delayMs
    }

    private companion object {
        // Coordinate uploads, acknowledgment retries, and HTTP backoff across SDK instances sharing a persisted queue.
        val deliveries = ConcurrentHashMap<String, QueueDeliveryState>()
    }
}
