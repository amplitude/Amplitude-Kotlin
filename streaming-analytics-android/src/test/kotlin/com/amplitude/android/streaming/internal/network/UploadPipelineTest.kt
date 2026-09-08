package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.storage.DelayedEventsQueue
import com.amplitude.android.streaming.internal.storage.DelayedEventsRequestEntity
import com.amplitude.android.streaming.internal.storage.toEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadPipelineTest {
    private val queue = mockk<DelayedEventsQueue>()
    private val endpoint = mockk<DelayedEventsEndpoint>()
    private var queued: DelayedEventsRequestEntity? = null

    @BeforeEach
    fun setUp() {
        queued = queuedRequest("stream-1")
        coEvery { queue.peek(any()) } answers {
            val skipIds = firstArg<Set<String>>()
            queued?.takeUnless { it.id in skipIds }
        }
        coEvery { queue.matches(any()) } returns true
        coEvery { queue.remove(any()) } answers { queued = null }
    }

    @Nested
    inner class Upload {
        @Test
        fun `does nothing when the queue is empty`() =
            runTest {
                queued = null

                pipeline().onNewEvent()

                coVerify(exactly = 0) { endpoint.send(any()) }
                coVerify(exactly = 0) { queue.remove(any()) }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `removes the request after a successful send`() =
            runTest {
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success

                pipeline().onNewEvent()

                coVerify { queue.peek(emptySet()) }
                coVerify { endpoint.send(match { it.id == "stream-1" }) }
                coVerify { queue.remove(match { it.id == "stream-1" }) }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `retries after failure backoff and removes on success`() =
            runTest {
                coEvery { endpoint.send(any()) } returnsMany
                    listOf(
                        DelayedEventsResult.Failure(statusCode = 500, message = "upstream down"),
                        DelayedEventsResult.Success,
                    )

                pipeline().onNewEvent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                coVerify { queue.remove(any()) }
                assertEquals(2_000L, currentTime)
            }

        @Test
        fun `waits at least 30 seconds after rate limit then retries`() =
            runTest {
                coEvery { endpoint.send(any()) } returnsMany
                    listOf(
                        DelayedEventsResult.RateLimited,
                        DelayedEventsResult.Success,
                    )

                pipeline().onNewEvent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                coVerify { queue.remove(any()) }
                assertEquals(30_000L, currentTime)
            }

        @Test
        fun `uses 2s, 4s, 8s, 16s, 32s then 64s backoff until success`() =
            runTest {
                coEvery { endpoint.send(any()) } returnsMany
                    listOf(
                        DelayedEventsResult.Failure(statusCode = 500, message = "1"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "2"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "3"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "4"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "5"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "6"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "7"),
                        DelayedEventsResult.Success,
                    )

                pipeline().onNewEvent()

                coVerify(exactly = 8) { endpoint.send(any()) }
                coVerify { queue.remove(any()) }
                assertEquals(
                    2_000L + 4_000L + 8_000L + 16_000L + 32_000L + 64_000L + 64_000L,
                    currentTime,
                )
            }

        @Test
        fun `rate limit waits the larger of 30s and current backoff`() =
            runTest {
                coEvery { endpoint.send(any()) } returnsMany
                    listOf(
                        DelayedEventsResult.Failure(statusCode = 500, message = "1"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "2"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "3"),
                        DelayedEventsResult.Failure(statusCode = 500, message = "4"),
                        DelayedEventsResult.RateLimited,
                        DelayedEventsResult.Success,
                    )

                pipeline().onNewEvent()

                coVerify(exactly = 6) { endpoint.send(any()) }
                assertEquals(2_000L + 4_000L + 8_000L + 16_000L + 32_000L, currentTime)
            }

        @Test
        fun `resets backoff after a successful send`() =
            runTest {
                val first = queuedRequest("stream-1")
                val second = queuedRequest("stream-2")
                queued = first
                coEvery { queue.remove(match { it.id == "stream-1" }) } answers { queued = second }
                coEvery { queue.remove(match { it.id == "stream-2" }) } answers { queued = null }
                coEvery { endpoint.send(any()) } returnsMany
                    listOf(
                        DelayedEventsResult.Failure(statusCode = 500, message = "upstream down"),
                        DelayedEventsResult.Success,
                        DelayedEventsResult.Failure(statusCode = 500, message = "upstream down"),
                        DelayedEventsResult.Success,
                    )

                pipeline().onNewEvent()

                coVerify(exactly = 4) { endpoint.send(any()) }
                assertEquals(2_000L + 2_000L, currentTime)
            }

        @Test
        fun `drains consecutive successful requests without delay`() =
            runTest {
                val first = queuedRequest("stream-1")
                val second = queuedRequest("stream-2")
                queued = first
                coEvery { queue.remove(match { it.id == "stream-1" }) } answers { queued = second }
                coEvery { queue.remove(match { it.id == "stream-2" }) } answers { queued = null }
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success

                pipeline().onNewEvent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `new upload waits out remaining backoff from a previous call`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns
                    DelayedEventsResult.Failure(statusCode = 500, message = "upstream down")
                val job = launch { pipeline.onNewEvent() }
                advanceTimeBy(400L)
                job.cancel()
                job.join()
                coVerify(exactly = 1) { endpoint.send(any()) }

                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
                pipeline.onNewEvent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                assertEquals(2_000L, currentTime)
            }

        @Test
        fun `concurrent onNewEvent returns while the drain loop is running`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns
                    DelayedEventsResult.Failure(statusCode = 500, message = "upstream down")
                val draining = launch { pipeline.onNewEvent() }
                advanceTimeBy(1L)
                coVerify(exactly = 1) { endpoint.send(any()) }

                val overlapping = launch { pipeline.onNewEvent() }
                testScheduler.runCurrent()

                assertEquals(true, overlapping.isCompleted)
                coVerify(exactly = 1) { endpoint.send(any()) }
                assertEquals(1L, currentTime)

                draining.cancel()
            }
    }

    @Nested
    inner class HeartbeatThrottle {
        @Test
        fun `does not resend an id until a quarter of its timeout has passed`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success

                pipeline.onNewEvent()
                coVerify(exactly = 1) { endpoint.send(any()) }

                queued = queuedRequest("stream-1")
                val retry = launch { pipeline.onNewEvent() }
                advanceTimeBy(1_249L)
                coVerify(exactly = 1) { endpoint.send(any()) }

                advanceTimeBy(1L)
                retry.join()
                coVerify(exactly = 2) { endpoint.send(any()) }
            }

        @Test
        fun `sends the next id instead of waiting on a throttled one`() =
            runTest {
                val pipeline = pipeline()
                val entries =
                    mutableListOf(queuedRequest("stream-1"), queuedRequest("stream-2"))
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
                coEvery { queue.peek(any()) } answers {
                    val skipIds = firstArg<Set<String>>()
                    entries.firstOrNull { it.id !in skipIds }
                }
                coEvery { queue.remove(any()) } answers {
                    entries.removeAll { it.id == firstArg<DelayedEventsRequestEntity>().id }
                }

                pipeline.onNewEvent()

                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-1" }) }
                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-2" }) }
                coVerify(exactly = 2) { endpoint.send(any()) }
            }

        @Test
        fun `retries a kept payload after the throttle window`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
                coEvery { queue.matches(any()) } returnsMany listOf(false, true)

                pipeline.onNewEvent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                coVerify { queue.remove(any()) }
                assertEquals(1_250L, currentTime)
            }
    }

    @Nested
    inner class Flush {
        @Test
        fun `sends a throttled id immediately`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success

                pipeline.onNewEvent()
                coVerify(exactly = 1) { endpoint.send(any()) }

                queued = queuedRequest("stream-1")
                pipeline.flush()

                coVerify(exactly = 2) { endpoint.send(any()) }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `returns while the drain loop is running`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns
                    DelayedEventsResult.Failure(statusCode = 500, message = "upstream down")
                val draining = launch { pipeline.onNewEvent() }
                advanceTimeBy(1L)

                val overlapping = launch { pipeline.flush() }
                testScheduler.runCurrent()

                assertEquals(true, overlapping.isCompleted)
                coVerify(exactly = 1) { endpoint.send(any()) }

                draining.cancel()
            }
    }

    private fun TestScope.pipeline(): UploadPipeline =
        UploadPipeline(
            queue = queue,
            endpoint = endpoint,
            currentTimeMs = { currentTime },
        )

    private fun queuedRequest(id: String): DelayedEventsRequestEntity =
        DelayedEventsRequestEntity(
            id = id,
            timeoutMillis = 5_000L,
            events =
                listOf(
                    DelayedEvent(
                        eventType = "[Amplitude] Stream Stopped",
                        kind = DelayedEvent.Kind.DELAYED,
                        timestamp = 1L,
                        eventProperties = mutableMapOf("stream_session_id" to id),
                    ).toEntity(),
                ),
            queueKey = "queue-$id",
        )
}
