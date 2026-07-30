package com.amplitude.core.utilities

import com.amplitude.core.RestrictedAmplitudeFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class, RestrictedAmplitudeFeature::class)
class ExponentialBackoffRetryHandlerTest {
    @Nested
    inner class BackoffSchedule {
        @Test
        fun `attemptRetry delays 1s, 8s, 32s, 128s then repeats 300s indefinitely`() =
            runTest {
                val handler = ExponentialBackoffRetryHandler()

                val expectedDelays =
                    listOf(1_000L, 8_000L, 32_000L, 128_000L) + List(10) { 300_000L }
                expectedDelays.forEachIndexed { attempt, expected ->
                    var canRetry = false
                    val elapsed = measureDelay { handler.attemptRetry { canRetry = it } }

                    assertTrue(canRetry, "expected to keep retrying on attempt $attempt")
                    assertWithinJitter(expected, elapsed, "attempt $attempt")
                }
            }

        @Test
        fun `attemptRetry increments the attempt count`() =
            runTest {
                val handler = ExponentialBackoffRetryHandler()

                handler.attemptRetry {}
                assertEquals(1, handler.attempt.get())
                handler.attemptRetry {}
                assertEquals(2, handler.attempt.get())
            }

        @Test
        fun `reset starts the schedule over`() =
            runTest {
                val handler = ExponentialBackoffRetryHandler()
                handler.attempt.set(4)

                handler.reset()

                assertEquals(0, handler.attempt.get())
                assertWithinJitter(1_000L, measureDelay { handler.attemptRetry {} }, "delay after reset")
            }

        @Test
        fun `jitter spreads a delay over plus or minus half of itself`() =
            runTest {
                val delays =
                    (1..100).map {
                        measureDelay { ExponentialBackoffRetryHandler().attemptRetry {} }
                    }

                delays.forEach { assertWithinJitter(1_000L, it, "jittered delay") }
                assertTrue(delays.distinct().size > 1, "expected jittered delays to vary, got $delays")
            }
    }

    /**
     * The virtual time a [block] of suspending calls spends delaying.
     */
    private suspend fun TestScope.measureDelay(block: suspend () -> Unit): Long {
        val startTime = currentTime
        block()
        return currentTime - startTime
    }

    private fun assertWithinJitter(
        expectedDelayInMs: Long,
        actualDelayInMs: Long,
        message: String,
    ) {
        val jitter = expectedDelayInMs / 2
        assertTrue(
            actualDelayInMs in (expectedDelayInMs - jitter)..(expectedDelayInMs + jitter),
            "$message: expected $actualDelayInMs to be within +/- $jitter of $expectedDelayInMs",
        )
    }
}
