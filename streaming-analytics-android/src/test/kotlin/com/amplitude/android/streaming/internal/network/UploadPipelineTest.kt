package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.StreamEvents
import com.amplitude.android.streaming.internal.storage.DelayedEventStorage
import com.amplitude.android.streaming.internal.storage.DelayedEventsQueue
import com.amplitude.android.streaming.internal.storage.DelayedEventsRequestEntity
import com.amplitude.android.streaming.internal.storage.QueueUnavailableException
import com.amplitude.android.streaming.internal.storage.toEntity
import com.amplitude.common.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class UploadPipelineTest {
    private val queue = mockk<DelayedEventsQueue>()
    private val endpoint = mockk<DelayedEventsEndpoint>()
    private val logger = mockk<Logger>(relaxed = true)
    private var queued: DelayedEventsRequestEntity? = null

    @BeforeEach
    fun setUp() {
        queued = queuedRequest("stream-1")
        coEvery { queue.peek(any()) } answers {
            val eligible = firstArg<(DelayedEventsRequestEntity) -> Boolean>()
            queued?.takeIf(eligible)
        }
        coEvery { queue.acknowledge(any()) } answers {
            queued = null
            true
        }
    }

    @Nested
    inner class Upload {
        @Test
        fun `should retain conflated triggers before the collector starts`() = runTest {
            val uploader = pipeline()
            coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
            repeat(100) { uploader.onNewEvent() }
            testScheduler.runCurrent()
            coVerify(exactly = 1) { endpoint.send(any()) }
            assertEquals(null, queued)
        }

        @Test
        fun `should drain a newer trigger arriving after the final queue read`() = runTest {
            val uploader = pipeline()
            coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
            var inserted = false
            coEvery { queue.peek(any()) } answers {
                val request = queued?.takeIf(firstArg<(DelayedEventsRequestEntity) -> Boolean>())
                if (request == null && !inserted) {
                    inserted = true
                    queued = instantRequest("stream-2")
                    uploader.onNewEvent()
                }
                request
            }

            uploader.onNewEvent()
            testScheduler.runCurrent()

            coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-1" }) }
            coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-2" }) }
            assertEquals(null, queued)
        }

        @Test
        fun `should recover the collector after an unexpected upload exception`() = runTest {
            val uploader = pipeline()
            var attempts = 0
            coEvery { endpoint.send(any()) } answers {
                if (++attempts == 1) error("unexpected transport failure")
                DelayedEventsResult.Success
            }
            uploader.onNewEvent()
            testScheduler.runCurrent()
            assertEquals(1, attempts)
            advanceTimeBy(2_000L)
            testScheduler.runCurrent()
            assertEquals(2, attempts)
            assertEquals(null, queued)
        }

        @ParameterizedTest
        @ValueSource(ints = [200, 400])
        fun `cleanup failures retry without resending or blocking other streams`(statusCode: Int) =
            runTest {
                var diskWritable = false
                val realQueue = inMemoryQueue { diskWritable }
                realQueue.enqueue(instantRequest("stream-1"))
                realQueue.enqueue(instantRequest("stream-2"))
                coEvery { endpoint.send(any()) } answers {
                    if (firstArg<DelayedEventsRequestDto>().id == "stream-1" && statusCode == 400) {
                        DelayedEventsResult.FailureNoRetry(statusCode, "invalid")
                    } else {
                        DelayedEventsResult.Success
                    }
                }
                pipeline(realQueue).onNewEvent()
                testScheduler.runCurrent()
                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-2" }) }
                advanceTimeBy(120_000L)
                testScheduler.runCurrent()
                coVerify(exactly = 2) { endpoint.send(any()) }

                diskWritable = true
                advanceTimeBy(64_001L)
                testScheduler.runCurrent()
                assertEquals(null, realQueue.peek())
                coVerify(exactly = 2) { endpoint.send(any()) }
            }

        @Test
        fun `does nothing when the queue is empty`() =
            runTest {
                queued = null

                pipeline().onNewEvent()
                testScheduler.runCurrent()

                coVerify(exactly = 0) { endpoint.send(any()) }
                coVerify(exactly = 0) { queue.acknowledge(any()) }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `removes the request after a successful send`() =
            runTest {
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success

                pipeline().onNewEvent()
                testScheduler.runCurrent()

                coVerify { queue.peek(any()) }
                coVerify { endpoint.send(match { it.id == "stream-1" }) }
                coVerify { queue.acknowledge(match { it.id == "stream-1" }) }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `drops an unrecoverable 400 without retrying`() =
            runTest {
                coEvery { endpoint.send(any()) } returns
                    DelayedEventsResult.FailureNoRetry(statusCode = 400, message = "invalid request")

                pipeline().onNewEvent()
                testScheduler.runCurrent()

                coVerify(exactly = 1) { endpoint.send(any()) }
                coVerify { queue.acknowledge(match { it.id == "stream-1" }) }
                verify {
                    logger.error(match { it.contains("stream-1") && it.contains("400") })
                }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `continues draining after dropping an unrecoverable request`() =
            runTest {
                val first = queuedRequest("stream-1")
                val second = queuedRequest("stream-2")
                queued = first
                coEvery { queue.acknowledge(match { it.id == "stream-1" }) } answers {
                    queued = second
                    true
                }
                coEvery { queue.acknowledge(match { it.id == "stream-2" }) } answers {
                    queued = null
                    true
                }
                coEvery { endpoint.send(any()) } returnsMany
                    listOf(
                        DelayedEventsResult.FailureNoRetry(statusCode = 400, message = "invalid request"),
                        DelayedEventsResult.Success,
                    )

                pipeline().onNewEvent()
                testScheduler.runCurrent()

                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-1" }) }
                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-2" }) }
                coVerify { queue.acknowledge(match { it.id == "stream-1" }) }
                coVerify { queue.acknowledge(match { it.id == "stream-2" }) }
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
                testScheduler.runCurrent()
                advanceTimeBy(2_000L)
                testScheduler.runCurrent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                coVerify { queue.acknowledge(any()) }
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
                testScheduler.runCurrent()
                advanceTimeBy(30_000L)
                testScheduler.runCurrent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                coVerify { queue.acknowledge(any()) }
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
                testScheduler.runCurrent()
                advanceTimeBy(190_000L)
                testScheduler.runCurrent()

                coVerify(exactly = 8) { endpoint.send(any()) }
                coVerify { queue.acknowledge(any()) }
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
                testScheduler.runCurrent()
                advanceTimeBy(62_000L)
                testScheduler.runCurrent()

                coVerify(exactly = 6) { endpoint.send(any()) }
                assertEquals(2_000L + 4_000L + 8_000L + 16_000L + 32_000L, currentTime)
            }

        @Test
        fun `resets backoff after a successful send`() =
            runTest {
                val first = queuedRequest("stream-1")
                val second = queuedRequest("stream-2")
                queued = first
                coEvery { queue.acknowledge(match { it.id == "stream-1" }) } answers {
                    queued = second
                    true
                }
                coEvery { queue.acknowledge(match { it.id == "stream-2" }) } answers {
                    queued = null
                    true
                }
                coEvery { endpoint.send(any()) } returnsMany
                    listOf(
                        DelayedEventsResult.Failure(statusCode = 500, message = "upstream down"),
                        DelayedEventsResult.Success,
                        DelayedEventsResult.Failure(statusCode = 500, message = "upstream down"),
                        DelayedEventsResult.Success,
                    )

                pipeline().onNewEvent()
                testScheduler.runCurrent()
                advanceTimeBy(4_000L)
                testScheduler.runCurrent()

                coVerify(exactly = 4) { endpoint.send(any()) }
                assertEquals(2_000L + 2_000L, currentTime)
            }

        @Test
        fun `drains consecutive successful requests without delay`() =
            runTest {
                val first = queuedRequest("stream-1")
                val second = queuedRequest("stream-2")
                queued = first
                coEvery { queue.acknowledge(match { it.id == "stream-1" }) } answers {
                    queued = second
                    true
                }
                coEvery { queue.acknowledge(match { it.id == "stream-2" }) } answers {
                    queued = null
                    true
                }
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success

                pipeline().onNewEvent()
                testScheduler.runCurrent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `new notifications preserve the existing retry deadline`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns
                    DelayedEventsResult.Failure(statusCode = 500, message = "upstream down")
                pipeline.onNewEvent()
                testScheduler.runCurrent()
                advanceTimeBy(400L)
                coVerify(exactly = 1) { endpoint.send(any()) }

                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
                pipeline.onNewEvent()
                testScheduler.runCurrent()
                advanceTimeBy(1_600L)
                testScheduler.runCurrent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                assertEquals(2_000L, currentTime)
            }

        @Test
        fun `concurrent onNewEvent returns while the drain loop is running`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns
                    DelayedEventsResult.Failure(statusCode = 500, message = "upstream down")
                pipeline.onNewEvent()
                advanceTimeBy(1L)
                coVerify(exactly = 1) { endpoint.send(any()) }

                val overlapping = launch { pipeline.onNewEvent() }
                testScheduler.runCurrent()

                assertEquals(true, overlapping.isCompleted)
                coVerify(exactly = 1) { endpoint.send(any()) }
                assertEquals(1L, currentTime)
            }

        @Test
        fun `uploads a request enqueued while the drain loop is running`() =
            runTest {
                val pipeline = pipeline()
                val entries = mutableListOf(queuedRequest("stream-1"))
                coEvery { queue.peek(any()) } answers {
                    val eligible = firstArg<(DelayedEventsRequestEntity) -> Boolean>()
                    entries.firstOrNull(eligible)
                }
                coEvery { queue.acknowledge(any()) } answers {
                    entries.removeAll { it.id == firstArg<DelayedEventsRequestEntity>().id }
                }
                coEvery { endpoint.send(any()) } coAnswers {
                    val id = firstArg<DelayedEventsRequestDto>().id
                    if (id == "stream-1") {
                        entries.add(queuedRequest("stream-2"))
                        launch { pipeline.onNewEvent() }
                        testScheduler.runCurrent()
                    }
                    DelayedEventsResult.Success
                }

                pipeline.onNewEvent()
                testScheduler.runCurrent()

                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-1" }) }
                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-2" }) }
            }
    }

    @Nested
    inner class InstantEvents {
        @ParameterizedTest
        @ValueSource(
            strings = [
                StreamEvents.STREAM_STARTED, StreamEvents.STREAM_STOPPED,
                StreamEvents.AD_STARTED, StreamEvents.AD_STOPPED, StreamEvents.AD_SKIPPED,
            ],
        )
        fun `should upload each pending instant event within the heartbeat window`(eventType: String) =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
                pipeline.onNewEvent()
                testScheduler.runCurrent()

                repeat(2) { index ->
                    queued = instantRequest("stream-1", eventType, "instant-$index")
                    pipeline.onNewEvent()
                    testScheduler.runCurrent()

                    coVerify(exactly = index + 2) { endpoint.send(any()) }
                    coVerify(exactly = 1) {
                        endpoint.send(match { it.instantEvents?.single()?.insertId == "instant-$index" })
                    }
                    assertEquals(null, queued)
                }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `uploads concurrent stop and restart then throttles their newer heartbeat`() =
            runTest {
                val realQueue = inMemoryQueue()
                val pipeline = pipeline(queue = realQueue)
                val start = instantRequest("stream-1", StreamEvents.STREAM_STARTED, "next-start").copy(
                    queueKey = null,
                    events = queuedRequest("stream-1", stopId = "next-stop").events,
                )
                val heartbeat = queuedRequest("stream-1", playTimeSec = 1.0, stopId = "next-stop").copy(queueKey = null)
                realQueue.enqueue(queuedRequest("stream-1").copy(queueKey = null))
                var sends = 0
                coEvery { endpoint.send(any()) } coAnswers {
                    // Each response races a newer update to the same stream session.
                    realQueue.enqueue(
                        when (++sends) {
                            1 -> instantRequest("stream-1", insertId = "stop-stream-1").copy(queueKey = null)
                            2 -> start
                            else -> heartbeat
                        },
                    )
                    DelayedEventsResult.Success
                }

                pipeline.onNewEvent()
                testScheduler.runCurrent()

                coVerify(exactly = 3) { endpoint.send(any()) }
                coVerify(exactly = 1) {
                    endpoint.send(match { it.instantEvents?.single()?.eventType == StreamEvents.STREAM_STARTED })
                }
                coVerify(exactly = 1) {
                    endpoint.send(match { it.instantEvents?.single()?.eventType == StreamEvents.STREAM_STOPPED })
                }
                val remaining = realQueue.peek()
                assertEquals(null, remaining?.instantEvents)
                assertEquals(heartbeat.events, remaining?.events)
                advanceTimeBy(DELAYED_EVENT_PULSE_INTERVAL_MILLIS - 1)
                coVerify(exactly = 3) { endpoint.send(any()) }
            }

        @Test
        fun `should send a clean stop with a live heartbeat past another throttled stream`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
                pipeline.onNewEvent()
                testScheduler.runCurrent()
                queued = queuedRequest("stream-2")
                pipeline.onNewEvent()
                testScheduler.runCurrent()

                val mixed =
                    queuedRequest("stream-2").copy(
                        instantEvents = instantRequest("stream-2").instantEvents,
                    )
                val entries = mutableListOf(queuedRequest("stream-1"), mixed)
                coEvery { queue.peek(any()) } answers {
                    val eligible = firstArg<(DelayedEventsRequestEntity) -> Boolean>()
                    entries.firstOrNull(eligible)
                }
                coEvery { queue.acknowledge(any()) } answers {
                    entries.remove(firstArg<DelayedEventsRequestEntity>())
                }

                pipeline.onNewEvent()
                testScheduler.runCurrent()

                coVerify(exactly = 1) {
                    endpoint.send(match { it.id == "stream-2" && it.events.isNotEmpty() && !it.instantEvents.isNullOrEmpty() })
                }
                assertEquals(listOf("stream-1"), entries.map { it.id })
                assertEquals(0L, currentTime)
            }
    }

    @Nested
    inner class HeartbeatThrottle {
        @Test
        fun `does not resend an id until the 60s pulse has passed`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success

                pipeline.onNewEvent()
                testScheduler.runCurrent()
                coVerify(exactly = 1) { endpoint.send(any()) }

                queued = queuedRequest("stream-1", playTimeSec = 1.0)
                pipeline.onNewEvent()
                testScheduler.runCurrent()
                advanceTimeBy(DELAYED_EVENT_PULSE_INTERVAL_MILLIS - 1)
                coVerify(exactly = 1) { endpoint.send(any()) }

                advanceTimeBy(1L)
                testScheduler.runCurrent()
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
                    val eligible = firstArg<(DelayedEventsRequestEntity) -> Boolean>()
                    entries.firstOrNull(eligible)
                }
                coEvery { queue.acknowledge(any()) } answers {
                    entries.removeAll { it.id == firstArg<DelayedEventsRequestEntity>().id }
                }

                pipeline.onNewEvent()
                testScheduler.runCurrent()

                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-1" }) }
                coVerify(exactly = 1) { endpoint.send(match { it.id == "stream-2" }) }
                coVerify(exactly = 2) { endpoint.send(any()) }
            }

        @Test
        fun `retries a kept payload after the throttle window`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success
                var unchanged = false
                coEvery { queue.acknowledge(any()) } answers {
                    if (unchanged) queued = null else unchanged = true
                    true
                }

                pipeline.onNewEvent()
                testScheduler.runCurrent()
                advanceTimeBy(DELAYED_EVENT_PULSE_INTERVAL_MILLIS)
                testScheduler.runCurrent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                coVerify(exactly = 2) { queue.acknowledge(any()) }
                assertEquals(DELAYED_EVENT_PULSE_INTERVAL_MILLIS, currentTime)
            }
    }

    @Nested
    inner class Flush {
        @Test
        fun `should await the final response and acknowledgment retry before finishing the drain`() = runTest {
            val uploader = pipeline()
            val response = CompletableDeferred<Unit>()
            coEvery { endpoint.send(any()) } coAnswers {
                response.await()
                DelayedEventsResult.Success
            }
            var acknowledgments = 0
            coEvery { queue.acknowledge(any()) } answers {
                if (++acknowledgments == 1) {
                    false
                } else {
                    queued = null
                    true
                }
            }
            val draining = launch { uploader.flushAndDrain() }
            testScheduler.runCurrent()
            assertEquals(false, draining.isCompleted)
            response.complete(Unit)
            testScheduler.runCurrent()
            assertEquals(false, draining.isCompleted)
            advanceTimeBy(1_999L)
            testScheduler.runCurrent()
            assertEquals(false, draining.isCompleted)
            advanceTimeBy(1L)
            testScheduler.runCurrent()
            assertEquals(true, draining.isCompleted)
            assertEquals(null, queued)
            coVerify(exactly = 1) { endpoint.send(any()) }
        }

        @Test
        fun `should cancel the drain waiter if its worker scope stops`() = runTest {
            val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
            val uploader = pipeline(scope = scope)
            val response = CompletableDeferred<Unit>()
            coEvery { endpoint.send(any()) } coAnswers {
                response.await()
                DelayedEventsResult.Success
            }
            var canceled = false
            val draining = launch {
                try {
                    uploader.flushAndDrain()
                } catch (_: CancellationException) {
                    canceled = true
                }
            }
            testScheduler.runCurrent()
            assertEquals(false, draining.isCompleted)
            scope.cancel()
            testScheduler.runCurrent()
            assertTrue(canceled)
            assertTrue(draining.isCompleted)
        }

        @Test
        fun `flush survives queue read and acknowledgment failures without another event`() = runTest {
            val uploader = pipeline()
            var readable = false
            coEvery { queue.peek(any()) } answers {
                if (!readable) throw QueueUnavailableException()
                queued?.takeIf(firstArg<(DelayedEventsRequestEntity) -> Boolean>())
            }
            var acknowledgments = 0
            coEvery { queue.acknowledge(any()) } answers {
                if (++acknowledgments == 1) {
                    false
                } else {
                    if (queued == firstArg<DelayedEventsRequestEntity>()) queued = null
                    true
                }
            }
            val sentAt = mutableListOf<Long>()
            coEvery { endpoint.send(any()) } answers {
                sentAt.add(currentTime)
                if (sentAt.size == 1) queued = queuedRequest("stream-1", playTimeSec = 1.0)
                DelayedEventsResult.Success
            }
            backgroundScope.launch { uploader.flush() }
            testScheduler.runCurrent()
            readable = true
            advanceTimeBy(3_000L)
            uploader.flush()
            testScheduler.runCurrent()
            advanceTimeBy(1_001L)
            testScheduler.runCurrent()
            assertEquals(listOf(2_000L, 4_000L), sentAt)
            assertEquals(null, queued)
        }

        @Test
        fun `should flush each stream once and throttle heartbeats arriving during the flush`() =
            runTest {
                val pipeline = pipeline()
                val entries = mutableListOf(queuedRequest("stream-1"), queuedRequest("stream-2"))
                coEvery { queue.peek(any()) } answers {
                    entries.firstOrNull(firstArg<(DelayedEventsRequestEntity) -> Boolean>())
                }
                coEvery { queue.acknowledge(any()) } returns true
                val sentIds = mutableListOf<String>()
                coEvery { endpoint.send(any()) } answers {
                    val id = firstArg<DelayedEventsRequestDto>().id
                    sentIds.add(id)
                    // A newer heartbeat remains queued after each upload. Bound the old bug's loop.
                    if (sentIds.size == 3) entries.clear()
                    DelayedEventsResult.Success
                }

                pipeline.flush()
                testScheduler.runCurrent()

                assertEquals(listOf("stream-1", "stream-2"), sentIds)
                advanceTimeBy(DELAYED_EVENT_PULSE_INTERVAL_MILLIS - 1)
                assertEquals(2, sentIds.size)
            }

        @Test
        fun `should honor a flush requested during an in flight upload`() =
            runTest {
                val pipeline = pipeline()
                val response = CompletableDeferred<Unit>()
                var sends = 0
                coEvery { endpoint.send(any()) } coAnswers {
                    if (++sends == 1) response.await()
                    DelayedEventsResult.Success
                }
                coEvery { queue.acknowledge(any()) } answers {
                    if (queued == firstArg<DelayedEventsRequestEntity>()) queued = null
                    true
                }
                pipeline.onNewEvent()
                testScheduler.runCurrent()
                queued = queuedRequest("stream-1", playTimeSec = 1.0)

                pipeline.flush()
                testScheduler.runCurrent()
                response.complete(Unit)
                testScheduler.runCurrent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                assertEquals(null, queued)
                assertEquals(0L, currentTime)
            }

        @Test
        fun `sends a throttled id immediately`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns DelayedEventsResult.Success

                pipeline.onNewEvent()
                testScheduler.runCurrent()
                coVerify(exactly = 1) { endpoint.send(any()) }

                queued = queuedRequest("stream-1")
                pipeline.flush()
                testScheduler.runCurrent()

                coVerify(exactly = 2) { endpoint.send(any()) }
                assertEquals(0L, currentTime)
            }

        @Test
        fun `returns while the drain loop is running`() =
            runTest {
                val pipeline = pipeline()
                coEvery { endpoint.send(any()) } returns
                    DelayedEventsResult.Failure(statusCode = 500, message = "upstream down")
                pipeline.onNewEvent()
                advanceTimeBy(1L)

                val overlapping = launch { pipeline.flush() }
                testScheduler.runCurrent()

                assertEquals(true, overlapping.isCompleted)
                coVerify(exactly = 1) { endpoint.send(any()) }
            }
    }

    private fun TestScope.pipeline(
        queue: DelayedEventsQueue = this@UploadPipelineTest.queue,
        scope: CoroutineScope = backgroundScope,
    ): UploadPipeline =
        UploadPipeline(
            scope = scope,
            queue = queue,
            endpoint = endpoint,
            logger = logger,
            currentTimeMs = { currentTime },
        )

    private fun inMemoryQueue(canDelete: () -> Boolean = { true }): DelayedEventsQueue {
        val entries = mutableMapOf<String, DelayedEventsRequestEntity>()
        val storage = mockk<DelayedEventStorage>()
        coEvery { storage.keys() } answers { entries.keys.toList() }
        coEvery { storage.findKey(any()) } answers { entries.keys.find { it.endsWith("-${firstArg<String>()}") } }
        coEvery { storage.read(any()) } answers {
            entries[firstArg<String>()]
        }
        coEvery { storage.write(any(), any()) } answers {
            entries[firstArg()] = secondArg()
            true
        }
        coEvery { storage.delete(any()) } answers {
            if (!canDelete()) {
                false
            } else {
                entries.remove(firstArg<String>())
                true
            }
        }
        return DelayedEventsQueue(storage = storage, logger = logger, storageKey = "uploader-acknowledgment-test")
    }

    private fun queuedRequest(
        id: String,
        playTimeSec: Double = 0.0,
        stopId: String = "stop-$id",
    ): DelayedEventsRequestEntity =
        DelayedEventsRequestEntity(
            id = id,
            timeoutMillis = 5_000L,
            events =
                listOf(
                    DelayedEvent(
                        eventType = StreamEvents.STREAM_STOPPED,
                        kind = DelayedEvent.Kind.DELAYED,
                        timestamp = 1L,
                        eventProperties =
                            mutableMapOf(
                                "[Streaming] Stream Session ID" to id,
                                "[Streaming] Play Time Sec" to playTimeSec,
                            ),
                    ).also { it.insertId = stopId }.toEntity(),
                ),
            queueKey = "queue-$id",
        )

    private fun instantRequest(
        id: String,
        eventType: String = StreamEvents.STREAM_STOPPED,
        insertId: String = "instant-$id",
    ): DelayedEventsRequestEntity =
        DelayedEventsRequestEntity(
            id = id,
            timeoutMillis = if (eventType == StreamEvents.STREAM_STOPPED) 0L else 3_600_000L,
            events = emptyList(),
            instantEvents = listOf(
                DelayedEvent(
                    eventType = eventType,
                    kind = DelayedEvent.Kind.INSTANT,
                    eventProperties = mutableMapOf("[Streaming] Stream Session ID" to id),
                ).also { it.insertId = insertId }.toEntity(),
            ),
        )
}
