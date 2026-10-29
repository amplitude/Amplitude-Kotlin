package com.amplitude.android.streaming.internal

import com.amplitude.android.streaming.internal.storage.toEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class DelayedEventBatcherTest {
    private val batcher = DelayedEventBatcher()

    @Test
    fun `should pass through events without batch metadata`() {
        val event = event(null, "single")

        assertSame(event, batcher.collect(event)!!.single())
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 3])
    fun `should wait for every member before returning a complete batch`(size: Int) {
        val info = BatchInfo(UUID.randomUUID(), size)
        val events = (0 until size).map { event(info, "event-$it") }.reversed()

        events.dropLast(1).forEach { assertNull(batcher.collect(it)) }
        val completed = batcher.collect(events.last())!!

        assertEquals(events.map { it.toEntity() }, completed.map { it.toEntity() })
    }

    @Test
    fun `should keep interleaved batches separate`() {
        val first = BatchInfo(UUID.randomUUID(), 2)
        val second = BatchInfo(UUID.randomUUID(), 2)

        assertNull(batcher.collect(event(first, "start")))
        assertNull(batcher.collect(event(second, "start")))
        val firstBatch = batcher.collect(event(first, "stop"))!!
        assertEquals(listOf("start", "stop"), firstBatch.map { it.insertId })
        val secondBatch = batcher.collect(event(second, "stop"))!!
        assertEquals(listOf("start", "stop"), secondBatch.map { it.insertId })
    }

    @Test
    fun `should snapshot enriched events before waiting for the rest of the batch`() {
        val info = BatchInfo(UUID.randomUUID(), 2)
        val values = mutableListOf("original")
        val nested = mutableMapOf<String, Any?>("values" to values)
        val properties = mutableMapOf<String, Any?>("nested" to nested)
        val first = event(info, "first").apply {
            eventProperties = properties
            timestamp = 1L
        }
        val expected = first.toEntity()

        assertNull(batcher.collect(first))
        properties["added"] = true
        values.add("changed")
        first.timestamp = 2L
        val completed = batcher.collect(event(info, "second"))!!

        assertNotSame(first, completed.first())
        assertEquals(expected, completed.first().toEntity())
        assertNull(completed.first().batchInfo)
    }

    @Test
    fun `should evict the oldest incomplete batch when the limit is reached`() {
        val infos = List(101) { BatchInfo(UUID.randomUUID(), 2) }
        infos.forEach { assertNull(batcher.collect(event(it, "first"))) }

        assertNull(batcher.collect(event(infos.first(), "second")))
        assertEquals(listOf("first", "second"), batcher.collect(event(infos.last(), "second"))!!.map { it.insertId })
    }

    private fun event(info: BatchInfo?, insertId: String): DelayedEvent =
        DelayedEvent(
            eventType = insertId,
            kind = DelayedEvent.Kind.DELAYED,
            batchInfo = info,
        ).also { it.insertId = insertId }
}
