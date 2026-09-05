package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.storage.DelayedEventsQueue
import com.amplitude.android.streaming.internal.storage.delayedEventsQueue
import com.amplitude.android.streaming.internal.storage.toDto
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds

internal val StreamingDiGraph.uploadPipeline: UploadPipeline by singleton {
    UploadPipeline(
        queue = delayedEventsQueue,
        endpoint = delayedEventsEndpoint,
    )
}

private val DELAYS_IN_MS = listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L)
private const val RATE_LIMIT_MIN_DELAY_MS = 30_000L

/**
 * Heartbeats rewrite the queued request locally, so only one of them has to reach the server
 * per interval. The interval stays under the request timeout because the server drops a delayed
 * event once its timeout lapses without another request for the same id.
 */
private class SentRequest(
    val atMs: Long,
    val minIntervalMs: Long,
)

internal class UploadPipeline(
    private val queue: DelayedEventsQueue,
    private val endpoint: DelayedEventsEndpoint,
    private val currentTimeMs: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()
    private var attempt = 0
    private var backoffUntilMs = 0L
    private val sent = mutableMapOf<String, SentRequest>()

    suspend fun onNewEvent() = upload(ignoreThrottle = false)

    suspend fun flush() = upload(ignoreThrottle = true)

    private suspend fun upload(ignoreThrottle: Boolean) {
        if (!mutex.tryLock()) return
        val retryWaitMs: Long
        try {
            while (true) {
                waitForBackoff()
                val skipIds = if (ignoreThrottle) emptySet() else throttledIds()
                val request = queue.peek(skipIds = skipIds) ?: break
                when (endpoint.send(request.toDto())) {
                    DelayedEventsResult.Success -> {
                        if (queue.matches(request)) {
                            queue.remove(request)
                        }
                        sent[request.id] =
                            SentRequest(
                                atMs = currentTimeMs(),
                                minIntervalMs = request.timeoutMillis / 4,
                            )
                        attempt = 0
                        backoffUntilMs = 0L
                    }
                    DelayedEventsResult.RateLimited -> {
                        scheduleBackoff(minDelayMs = RATE_LIMIT_MIN_DELAY_MS)
                    }
                    is DelayedEventsResult.Failure -> {
                        scheduleBackoff(minDelayMs = 0L)
                    }
                }
            }
            retryWaitMs = throttleRetryWaitMs()
        } finally {
            mutex.unlock()
        }
        if (retryWaitMs > 0) {
            delay(retryWaitMs.milliseconds)
            upload(ignoreThrottle = false)
        }
    }

    private suspend fun throttleRetryWaitMs(): Long {
        if (queue.peek(skipIds = emptySet()) == null) return 0L
        val now = currentTimeMs()
        return sent.minOfOrNull { (_, sent) -> sent.atMs + sent.minIntervalMs - now }
            ?.coerceAtLeast(0L)
            ?: 0L
    }

    private fun throttledIds(): Set<String> {
        val now = currentTimeMs()
        sent.entries.removeAll { (_, request) -> now - request.atMs >= request.minIntervalMs }
        return sent.keys.toSet()
    }

    private suspend fun waitForBackoff() {
        val remainingMs = backoffUntilMs - currentTimeMs()
        if (remainingMs > 0) {
            delay(remainingMs.milliseconds)
        }
    }

    private fun scheduleBackoff(minDelayMs: Long) {
        val delayMs =
            max(
                minDelayMs,
                DELAYS_IN_MS[attempt.coerceAtMost(DELAYS_IN_MS.lastIndex)],
            )
        attempt++
        backoffUntilMs = currentTimeMs() + delayMs
    }
}
