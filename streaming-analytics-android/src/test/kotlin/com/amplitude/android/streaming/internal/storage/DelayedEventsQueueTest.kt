package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.common.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DelayedEventsQueueTest {
    private val storage = mockk<DelayedEventStorage>()
    private val logger = mockk<Logger>(relaxed = true)
    private lateinit var queue: DelayedEventsQueue

    @BeforeEach
    fun setUp() {
        coEvery { storage.keys() } returns emptyList()
        queue = DelayedEventsQueue(storage = storage, logger = logger)
    }

    @Nested
    inner class Enqueue {
        @Test
        fun `instant update keeps existing delayed events and timeout`() =
            runTest {
                val delayed = eventEntity("stopped")
                val instant = eventEntity("started")
                coEvery { storage.findKey(any()) } returns "existing-key"
                coEvery { storage.read("existing-key") } returns
                    DelayedEventsRequestEntity(
                        id = "stream-1",
                        timeoutMillis = 5_000L,
                        events = listOf(delayed),
                    )
                coEvery { storage.write(any(), any()) } returns Unit

                queue.enqueue(
                    DelayedEventsRequestEntity(
                        id = "stream-1",
                        timeoutMillis = 0L,
                        events = emptyList(),
                        instantEvents = listOf(instant),
                    ),
                )

                coVerify {
                    storage.write(
                        "existing-key",
                        match { stored ->
                            stored.events == listOf(delayed) &&
                                stored.timeoutMillis == 5_000L &&
                                stored.instantEvents == listOf(instant)
                        },
                    )
                }
            }

        @Test
        fun `delayed update replaces delayed events and timeout`() =
            runTest {
                val previousDelayed = eventEntity("stopped-old")
                val nextDelayed = eventEntity("stopped-new")
                coEvery { storage.findKey(any()) } returns "existing-key"
                coEvery { storage.read("existing-key") } returns
                    DelayedEventsRequestEntity(
                        id = "stream-1",
                        timeoutMillis = 5_000L,
                        events = listOf(previousDelayed),
                        instantEvents = listOf(eventEntity("started")),
                    )
                coEvery { storage.write(any(), any()) } returns Unit

                queue.enqueue(
                    DelayedEventsRequestEntity(
                        id = "stream-1",
                        timeoutMillis = 8_000L,
                        events = listOf(nextDelayed),
                    ),
                )

                coVerify {
                    storage.write(
                        "existing-key",
                        match { stored ->
                            stored.events == listOf(nextDelayed) &&
                                stored.timeoutMillis == 8_000L &&
                                stored.instantEvents?.size == 1
                        },
                    )
                }
            }
    }

    @Nested
    inner class Peek {
        @Test
        fun `reads the oldest key first`() =
            runTest {
                coEvery { storage.keys() } returns
                    listOf("0000000000000000002-bbb", "0000000000000000001-aaa")
                coEvery { storage.read("0000000000000000001-aaa") } returns request("stream-1")

                val peeked = queue.peek(skipIds = emptySet())

                assertEquals("0000000000000000001-aaa", peeked?.queueKey)
                assertEquals("stream-1", peeked?.id)
            }

        @Test
        fun `skips entries whose id is throttled`() =
            runTest {
                coEvery { storage.keys() } returns
                    listOf("0000000000000000001-aaa", "0000000000000000002-bbb")
                coEvery { storage.read("0000000000000000001-aaa") } returns request("stream-1")
                coEvery { storage.read("0000000000000000002-bbb") } returns request("stream-2")

                val peeked = queue.peek(skipIds = setOf("stream-1"))

                assertEquals("stream-2", peeked?.id)
            }

        @Test
        fun `returns null when every entry is skipped`() =
            runTest {
                coEvery { storage.keys() } returns listOf("0000000000000000001-aaa")
                coEvery { storage.read("0000000000000000001-aaa") } returns request("stream-1")

                assertNull(queue.peek(skipIds = setOf("stream-1")))
            }

        @Test
        fun `drops a corrupt entry and moves to the next one`() =
            runTest {
                coEvery { storage.keys() } returns
                    listOf("0000000000000000001-aaa", "0000000000000000002-bbb")
                coEvery { storage.read("0000000000000000001-aaa") } throws
                    SerializationException("truncated")
                coEvery { storage.read("0000000000000000002-bbb") } returns request("stream-2")
                coEvery { storage.delete(any()) } returns Unit

                val peeked = queue.peek(skipIds = emptySet())

                assertEquals("stream-2", peeked?.id)
                coVerify { storage.delete("0000000000000000001-aaa") }
            }
    }

    @Nested
    inner class Matches {
        @Test
        fun `is true when the on-disk payload is unchanged`() =
            runTest {
                val stored = request("stream-1")
                coEvery { storage.read("queue-1") } returns stored

                assertEquals(
                    true,
                    queue.matches(stored.copy(queueKey = "queue-1")),
                )
            }

        @Test
        fun `is false when the on-disk payload changed`() =
            runTest {
                coEvery { storage.read("queue-1") } returns request("stream-1")

                assertEquals(
                    false,
                    queue.matches(
                        request("stream-1").copy(
                            timeoutMillis = 9_000L,
                            queueKey = "queue-1",
                        ),
                    ),
                )
            }
    }

    private fun request(id: String): DelayedEventsRequestEntity =
        DelayedEventsRequestEntity(
            id = id,
            timeoutMillis = 5_000L,
            events = listOf(eventEntity("stopped")),
        )

    private fun eventEntity(eventType: String): DelayedEventEntity =
        DelayedEvent(
            eventType = eventType,
            kind = DelayedEvent.Kind.DELAYED,
            timestamp = 1L,
            eventProperties = mutableMapOf(),
        ).toEntity()
}
