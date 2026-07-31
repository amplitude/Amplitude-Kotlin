package com.amplitude.core.utilities

import com.amplitude.core.RestrictedAmplitudeFeature
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A utility class to handle retry backoff logic.
 *
 * Delays follow a fixed schedule of 1s, 8s, 32s, 128s and then plateau at 300s, which is repeated
 * for as long as the retries keep failing. Every delay is jittered by +/- half of itself so retries
 * from different clients spread out.
 *
 * Usage:
 * - call [attemptRetry] to attempt retry with a backoff delay
 * - call [reset] on success, so the next failure starts the schedule over.
 */
@RestrictedAmplitudeFeature
public class ExponentialBackoffRetryHandler() {
    @Deprecated(
        "The backoff schedule is fixed now, baseDelayInMs and factor are ignored.",
        ReplaceWith("ExponentialBackoffRetryHandler()"),
    )
    @RestrictedAmplitudeFeature
    public constructor(
        maxRetryAttempt: Int = 5,
        baseDelayInMs: Int,
        factor: Double,
    ) : this()

    @Deprecated("The backoff schedule no longer has a max retry cap. This is ignored and will be removed.")
    public val maxRetryAttempt: Int = 5

    /**
     * After we've reached [maxRetryAttempt], we will stop retrying for a longer period and use this
     * value.
     */
    @Deprecated("Unused. Will be removed.")
    public val maxDelayInMs: Long
        get() = DELAYS_IN_MS.last()

    internal var attempt = AtomicInteger(0)

    /**
     * Attempt retry with the backoff delay of the current attempt. see [nextDelay]
     * @param block a lambda to execute the retry logic. The lambda will receive a boolean parameter to indicate if the retry logic should be executed.
     * Retries are no longer capped, so the parameter is always true.
     */
    public suspend fun attemptRetry(block: (Boolean) -> Unit) {
        delay(nextDelay())
        block(true)
        attempt.incrementAndGet()
    }

    /**
     * Reset the retry attempt counter, so the next retry starts the backoff schedule over.
     */
    public fun reset() {
        attempt.set(0)
    }

    /**
     * The jittered delay of the current attempt.
     */
    internal fun nextDelay(): Duration {
        val delayInMs = DELAYS_IN_MS[attempt.get().coerceAtMost(DELAYS_IN_MS.lastIndex)]
        return jitter(delayInMs).milliseconds
    }

    /**
     * Spread [delayInMs] over +/- half of itself, e.g. 8s becomes a delay within 4s and 12s.
     */
    private fun jitter(delayInMs: Long): Long = delayInMs / 2 + Random.nextLong(delayInMs + 1)

    public companion object {
        /**
         * The backoff delay of each attempt, the last one is reused for every attempt after it.
         */
        private val DELAYS_IN_MS = listOf(1_000L, 8_000L, 32_000L, 128_000L, 300_000L)
    }
}
