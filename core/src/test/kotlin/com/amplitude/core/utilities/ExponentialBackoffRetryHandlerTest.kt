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
    fun `canRetry returns properly until maxRetryAttempt is reached`() = runBlocking {
        val handler = ExponentialBackoffRetryHandler(
            maxRetryAttempt = 2,
            baseDelayInMs = 10
        )
        repeat(2) {
            assertTrue(handler.canRetry())
            handler.retryWithDelay {}
        }
        assertFalse(handler.canRetry())
    }

    @Test
    fun `retryWithDelay increments attempts within max range`() = runTest {
        val handler = ExponentialBackoffRetryHandler(maxRetryAttempt = 2)

        handler.retryWithDelay {}
        assertEquals(1, handler.attempt.get())
        handler.retryWithDelay {}
        assertEquals(2, handler.attempt.get())
        handler.retryWithDelay {}
        assertEquals(2, handler.attempt.get()) // max attempt reached
    }

    @Test
    fun `reset sets attempts to zero`() = runBlocking {
        val handler = ExponentialBackoffRetryHandler(maxRetryAttempt = 3, baseDelayInMs = 10)
        repeat(3) {
            handler.retryWithDelay {}
        }
        handler.reset()
        assertEquals(0, handler.attempt.get())
    }

    @Test
    fun `retryWithDelay respects exponential backoff`() = runBlocking {
        val baseDelayInMs = 10
        val attemptNumber = 4
        val handler = ExponentialBackoffRetryHandler(
            baseDelayInMs = baseDelayInMs
        )
        handler.attempt.set(attemptNumber)

        val startTime = System.currentTimeMillis()
        handler.retryWithDelay {
            val endTime = System.currentTimeMillis()
            val elapsedTime = endTime - startTime

            val expected = baseDelayInMs * 2.0.pow(attemptNumber)
            assertTrue(elapsedTime >= expected)
        }
    }
}
