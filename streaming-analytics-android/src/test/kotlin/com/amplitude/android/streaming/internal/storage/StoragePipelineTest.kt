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
    inner class OnDelayedEvent {
        @Test
        fun `uses a one hour server timeout`() {
            assertEquals(3_600_000L, DELAYED_EVENT_TIMEOUT_MILLIS)
        }

        @ParameterizedTest
        @ValueSource(strings = [StreamEvents.STREAM_STOPPED, StreamEvents.AD_STOPPED])
        fun `delayed kind enqueues the event on the request events list`(eventType: String) =
            runTest {
                pipeline.onDelayedEvent(
                    delayedEvent(kind = DelayedEvent.Kind.DELAYED).apply { this.eventType = eventType },
                )

                coVerify {
                    queue.enqueue(
                        match { request ->
                            request.id == "stream-1" &&
                                request.timeoutMillis == DELAYED_EVENT_TIMEOUT_MILLIS &&
                                request.events.size == 1 &&
                                request.instantEvents == null
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
                pipeline.onDelayedEvent(
                    delayedEvent(kind = DelayedEvent.Kind.INSTANT).apply { this.eventType = eventType },
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

        @Test
        fun `drops events that are missing the stream session id`() =
            runTest {
                pipeline.onDelayedEvent(
                    DelayedEvent(
                        eventType = "[Streaming] Stream Stopped",
                        kind = DelayedEvent.Kind.DELAYED,
                        timestamp = 1L,
                        eventProperties = mutableMapOf(),
                    ),
                )

                coVerify(exactly = 0) { queue.enqueue(any()) }
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
