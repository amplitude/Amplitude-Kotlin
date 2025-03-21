package com.amplitude.core.utilities

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

/**
 * A utility class to handle exponential backoff retry logic.
 */
class ExponentialBackoffRetryHandler(
    val maxRetryAttempt: Int = MAX_RETRY_ATTEMPT,
    private val baseDelayInMs: Int = 1_000,
    private val factor: Double = 2.0,
) {
    internal var attempt = AtomicInteger(0)

    fun canRetry() = attempt.get() < maxRetryAttempt

    suspend fun <T> retryWithDelay(block: suspend () -> T) {
        if (!canRetry()) return
        // delay is exponentially increasing based on factor^attempt
        // factor = 2.0: 1, 2, 4, 8, 16 seconds
        delay((baseDelayInMs * factor.pow(attempt.get())).toLong())
        block()
        attempt.incrementAndGet()
    }

    fun reset() {
        attempt.set(0)
    }

    companion object {
        private const val MAX_RETRY_ATTEMPT = 5
    }
}
