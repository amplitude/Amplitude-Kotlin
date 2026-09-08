package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.common.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
        fun `delayed kind enqueues the event on the request events list`() =
            runTest {
                pipeline.onDelayedEvent(
                    delayedEvent(kind = DelayedEvent.Kind.DELAYED),
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

        @Test
        fun `instant kind enqueues the event on instantEvents`() =
            runTest {
                pipeline.onDelayedEvent(
                    delayedEvent(kind = DelayedEvent.Kind.INSTANT),
                )

                coVerify {
                    queue.enqueue(
                        match { request ->
                            request.id == "stream-1" &&
                                request.events.isEmpty() &&
                                request.instantEvents?.size == 1
                        },
                    )
                }
            }

        @Test
        fun `drops events that are missing stream_session_id`() =
            runTest {
                pipeline.onDelayedEvent(
                    DelayedEvent(
                        eventType = "[Amplitude] Stream Stopped",
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
            eventType = "[Amplitude] Stream Stopped",
            kind = kind,
            timestamp = 1L,
            eventProperties = mutableMapOf("stream_session_id" to "stream-1"),
        )
}
