package com.amplitude.core.utilities

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.pow

/**
 * A utility class to handle exponential backoff retry logic.
 *
 * Usage:
 * - call [attemptRetry] to attempt retry with exponential backoff delay
 * - always call [reset] at the end of your session to reset the retry attempt counter.
 */
class ExponentialBackoffRetryHandler(
    val maxRetryAttempt: Int = MAX_RETRY_ATTEMPT,
    private val baseDelayInMs: Int = 1_000,
    private val factor: Double = 2.0,
) {
    /**
     * The current exponential backoff delay in milliseconds. Formula is [baseDelayInMs] * ([factor]^[attempt])
     *
     * e.g. for the default values, it will be: 1, 2, 4, 8, 16 seconds
     */
    val exponentialBackOffDelayInMs
        get() = (baseDelayInMs * factor.pow(attempt.get())).toLong()

    internal var attempt = AtomicInteger(0)

    private fun canRetry() = attempt.get() < maxRetryAttempt

    /**
     * Attempt retry with exponential backoff delay. see [exponentialBackOffDelayInMs]
     * @param block a lambda to execute the retry logic. The lambda will receive a boolean parameter to indicate if the retry logic should be executed.
     * If boolean parameter is false, [maxRetryAttempt] was reached and you should stop retrying and handle the failure.
     */
    suspend fun attemptRetry(block: (Boolean) -> Unit) {
        if (!canRetry()) {
            block(false)
            return
        }
        delay(exponentialBackOffDelayInMs)
        block(true)
        attempt.incrementAndGet()
    }

    /**
     * Reset the retry attempt counter.
     */
    fun reset() {
        attempt.set(0)
    }

    companion object {
        private const val MAX_RETRY_ATTEMPT = 5
    }
}
