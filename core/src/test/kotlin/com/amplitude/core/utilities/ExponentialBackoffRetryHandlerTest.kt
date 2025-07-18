package com.amplitude.core.utilities

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class ExponentialBackoffRetryHandlerTest {
    @Test
    fun `attemptRetry returns properly until maxRetryAttempt is reached`() =
        runBlocking {
            val handler =
                ExponentialBackoffRetryHandler(
                    maxRetryAttempt = 2,
                    baseDelayInMs = 10,
                )
            repeat(3) { count ->
                handler.attemptRetry { canRetry ->
                    if (count < 2) {
                        assertTrue(canRetry)
                    } else {
                        assertFalse(canRetry)
                    }
                }
            }
        }

    @Test
    fun `retryWithDelay increments attempts within max range`() =
        runTest {
            val handler = ExponentialBackoffRetryHandler(maxRetryAttempt = 2)

            handler.attemptRetry {}
            assertEquals(1, handler.attempt.get())
            handler.attemptRetry {}
            assertEquals(2, handler.attempt.get())
            handler.attemptRetry {}
            assertEquals(2, handler.attempt.get()) // max attempt reached
        }

    @Test
    fun `reset sets attempts to zero`() =
        runBlocking {
            val handler = ExponentialBackoffRetryHandler(maxRetryAttempt = 3, baseDelayInMs = 10)
            repeat(4) { count ->
                handler.attemptRetry { canRetry ->
                    if (count < 3) {
                        assertTrue(canRetry)
                    } else {
                        assertFalse(canRetry)
                    }
                }
            }
            handler.reset()
            assertEquals(0, handler.attempt.get())
            handler.attemptRetry { canRetry ->
                assertTrue(canRetry)
            }
        }

    @Test
    fun `attemptRetry respects exponential backoff`() =
        runBlocking {
            val baseDelayInMs = 10
            val attemptNumber = 4
            val handler =
                ExponentialBackoffRetryHandler(
                    baseDelayInMs = baseDelayInMs,
                )
            handler.attempt.set(attemptNumber)

            val startTime = System.currentTimeMillis()
            handler.attemptRetry { canRetry ->
                assertTrue(canRetry)
                val endTime = System.currentTimeMillis()
                val elapsedTime = endTime - startTime

                val expected = baseDelayInMs * 2.0.pow(attemptNumber)
                assertTrue(elapsedTime >= expected)
            }
        }

    @Test
    fun `maxDelay respects ceiling value and current attempt count`() {
        val maxRetryAttempt = 10
        var handler =
            ExponentialBackoffRetryHandler(
                maxRetryAttempt = maxRetryAttempt,
            )

        // should be set to ceiling of 60 seconds
        handler.attempt.set(10)
        assertEquals(handler.maxDelayInMs, 60_000)

        // should be set to 2^5: 2^(maxRetryAttempt + 1)
        handler =
            ExponentialBackoffRetryHandler(
                maxRetryAttempt = 4,
            )
        handler.attempt.set(4)
        assertEquals(handler.maxDelayInMs, 32_000)
    }
}
