package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.android.streaming.internal.StreamEvents
import com.amplitude.common.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

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
