package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.StreamEvents
import com.amplitude.common.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@OptIn(ExperimentalCoroutinesApi::class)
class StoragePipelineTest {
    private val queue = mockk<DelayedEventsQueue>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private lateinit var pipeline: StoragePipeline

    @BeforeEach
    fun setUp() {
        coEvery { queue.enqueue(any()) } returns Unit
        pipeline = StoragePipeline(queue = queue, logger = logger)
    }

    @Nested
    inner class OnDelayedEvents {
        @ParameterizedTest
        @ValueSource(strings = ["read", "keys", "write"])
        fun `should retain a clean stop across transient persistence failure`(failure: String) = runTest {
            val storage = mockk<DelayedEventStorage>()
            val realQueue = DelayedEventsQueue(storage, logger, "storage-retry-$failure")
            val realPipeline = StoragePipeline(realQueue, logger)
            val fallback = delayedEvent(DelayedEvent.Kind.DELAYED).also { it.insertId = "stop" }.toEntity()
            val start = delayedEvent(DelayedEvent.Kind.INSTANT).also { it.insertId = "start" }.toEntity()
            val previous = DelayedEventsRequestEntity("stream-1", DELAYED_EVENT_TIMEOUT_MILLIS, listOf(fallback), listOf(start))
            var attempts = 0
            var written: DelayedEventsRequestEntity? = null
            coEvery { storage.findKey(any()) } answers {
                if (failure == "keys" && attempts == 0) null else "existing"
            }
            coEvery { storage.keys() } answers {
                attempts++
                throw QueueUnavailableException()
            }
            coEvery { storage.read("existing") } answers {
                if (failure == "read" && attempts++ == 0) throw QueueUnavailableException()
                previous
            }
            coEvery { storage.write(any(), any()) } answers {
                if (failure == "write" && attempts++ == 0) throw QueueUnavailableException()
                written = secondArg()
                true
            }
            val stop = delayedEvent(DelayedEvent.Kind.INSTANT).also { it.insertId = "stop" }
            realPipeline.onDelayedEvents(listOf(stop))

            assertEquals(0L, currentTime)
            assertEquals(emptyList<DelayedEventEntity>(), written?.events)
            assertEquals(listOf(start, stop.toEntity()), written?.instantEvents)
            assertEquals(0L, written?.timeoutMillis)
        }

        @Test
        fun `should keep an older heartbeat retry ahead of the final stop`() = runTest {
            val persisted = mutableListOf<DelayedEventsRequestEntity>()
            val releaseFirstWrite = CompletableDeferred<Unit>()
            var attempts = 0
            coEvery { queue.enqueue(any()) } coAnswers {
                if (++attempts == 1) {
                    releaseFirstWrite.await()
                    throw QueueUnavailableException()
                }
                persisted.add(firstArg())
                Unit
            }
            val heartbeat = launch { pipeline.onDelayedEvents(listOf(delayedEvent(DelayedEvent.Kind.DELAYED))) }
            runCurrent()
            val stop = launch { pipeline.onDelayedEvents(listOf(delayedEvent(DelayedEvent.Kind.INSTANT))) }
            runCurrent()
            advanceTimeBy(1_500L)
            runCurrent()
            assertFalse(heartbeat.isCompleted)
            assertFalse(stop.isCompleted)
            assertEquals(1, attempts)
            releaseFirstWrite.complete(Unit)
            heartbeat.join()
            stop.join()

            assertEquals(listOf(DELAYED_EVENT_TIMEOUT_MILLIS, 0L), persisted.map { it.timeoutMillis })
            assertEquals(1_500L, currentTime)
        }

        @Test
        fun `should stop retrying a failed update and continue other streams and later calls`() = runTest {
            val persisted = mutableListOf<String>()
            coEvery { queue.enqueue(any()) } answers {
                val request = firstArg<DelayedEventsRequestEntity>()
                if (request.id == "stream-1") throw QueueUnavailableException()
                persisted.add(request.id)
                Unit
            }
            val failed = delayedEvent(DelayedEvent.Kind.INSTANT)
            val other = delayedEvent(DelayedEvent.Kind.INSTANT).apply {
                eventProperties?.put("[Streaming] Stream Session ID", "stream-2")
            }

            pipeline.onDelayedEvents(listOf(failed, other))

            assertEquals(listOf("stream-2"), persisted)
            assertEquals(0L, currentTime)
            coVerify(exactly = 2) { queue.enqueue(match { it.id == "stream-1" }) }
            verify(exactly = 1) { logger.error(match { it.contains("stream-1") && it.contains("dropping update") }) }

            coEvery { queue.enqueue(any()) } answers {
                persisted.add(firstArg<DelayedEventsRequestEntity>().id)
                Unit
            }
            pipeline.onDelayedEvents(listOf(failed))
            assertEquals(listOf("stream-2", "stream-1"), persisted)
        }

        @Test
        fun `uses a one hour server timeout`() {
            assertEquals(3_600_000L, DELAYED_EVENT_TIMEOUT_MILLIS)
        }

        @ParameterizedTest
        @ValueSource(strings = [StreamEvents.STREAM_STOPPED, StreamEvents.AD_STOPPED])
        fun `delayed kind enqueues the event on the request events list`(eventType: String) =
            runTest {
                pipeline.onDelayedEvents(
                    listOf(delayedEvent(kind = DelayedEvent.Kind.DELAYED).apply { this.eventType = eventType }),
                )

                coVerify {
                    queue.enqueue(
                        match { request ->
                            request.id == "stream-1" &&
                                request.timeoutMillis == DELAYED_EVENT_TIMEOUT_MILLIS &&
                                request.events.size == 1 &&
                                request.instantEvents?.isEmpty() == true
                        },
                    )
                }
            }

        @ParameterizedTest
        @CsvSource(
            "[Streaming] Stream Stopped, 0",
            "[Streaming] Ad Stopped, 3600000",
            "[Streaming] Stream Started, 3600000",
            "[Streaming] Ad Started, 3600000",
            "[Streaming] Ad Skipped, 3600000",
        )
        fun `instant kind enqueues the event on instantEvents`(eventType: String, expectedTimeout: Long) =
            runTest {
                pipeline.onDelayedEvents(
                    listOf(delayedEvent(kind = DelayedEvent.Kind.INSTANT).apply { this.eventType = eventType }),
                )

                coVerify {
                    queue.enqueue(
                        match { request ->
                            request.id == "stream-1" &&
                                request.timeoutMillis == expectedTimeout &&
                                request.events.isEmpty() &&
                                request.instantEvents?.size == 1
                        },
                    )
                }
            }

        @ParameterizedTest
        @ValueSource(strings = ["stream-1", "stream-2"])
        fun `should enqueue exactly one request per stream session`(stopSessionId: String) =
            runTest {
                val start = delayedEvent(DelayedEvent.Kind.INSTANT).apply { eventType = StreamEvents.STREAM_STARTED }
                val stop = delayedEvent(DelayedEvent.Kind.DELAYED)
                stop.eventProperties?.put("[Streaming] Stream Session ID", stopSessionId)
                val requests = mutableListOf<DelayedEventsRequestEntity>()
                coEvery { queue.enqueue(capture(requests)) } returns Unit

                pipeline.onDelayedEvents(listOf(start, stop))

                val expectedIds = if (stopSessionId == "stream-1") listOf("stream-1") else listOf("stream-1", "stream-2")
                assertEquals(expectedIds, requests.map { it.id })
                assertEquals(listOf(start.toEntity()), requests[0].instantEvents)
                if (stopSessionId == "stream-1") {
                    assertEquals(listOf(stop.toEntity()), requests.single().events)
                } else {
                    assertEquals(emptyList<DelayedEventEntity>(), requests[0].events)
                    assertEquals(listOf(stop.toEntity()), requests[1].events)
                    assertEquals(emptyList<DelayedEventEntity>(), requests[1].instantEvents)
                }
            }

        @ParameterizedTest
        @ValueSource(booleans = [false, true])
        fun `drops events that are missing the stream session id`(batched: Boolean) =
            runTest {
                val invalid =
                    DelayedEvent(
                        eventType = "[Streaming] Stream Stopped",
                        kind = DelayedEvent.Kind.DELAYED,
                        timestamp = 1L,
                        eventProperties = mutableMapOf(),
                    )
                val events =
                    if (batched) listOf(delayedEvent(DelayedEvent.Kind.INSTANT), invalid) else listOf(invalid)
                pipeline.onDelayedEvents(events)

                coVerify(exactly = if (batched) 1 else 0) { queue.enqueue(any()) }
                coVerify(exactly = 0) { queue.enqueue(match { it.id.isBlank() }) }
            }
    }

    private fun delayedEvent(kind: DelayedEvent.Kind): DelayedEvent =
        DelayedEvent(
            eventType = "[Streaming] Stream Stopped",
            kind = kind,
            timestamp = 1L,
            eventProperties = mutableMapOf("[Streaming] Stream Session ID" to "stream-1"),
        )
}
