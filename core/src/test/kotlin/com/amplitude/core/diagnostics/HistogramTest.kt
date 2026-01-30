package com.amplitude.core.diagnostics

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistogramTest {
    @Test
    fun `HistogramStats records single value correctly`() {
        val stats = HistogramStats()
        stats.record(5.0)

        val snapshot = stats.toSnapshot()
        assertEquals(1L, snapshot.count)
        assertEquals(5.0, snapshot.min)
        assertEquals(5.0, snapshot.max)
        assertEquals(5.0, snapshot.sum)
    }

    @Test
    fun `HistogramStats records multiple values correctly`() {
        val stats = HistogramStats()
        stats.record(10.0)
        stats.record(5.0)
        stats.record(20.0)
        stats.record(15.0)

        val snapshot = stats.toSnapshot()
        assertEquals(4L, snapshot.count)
        assertEquals(5.0, snapshot.min)
        assertEquals(20.0, snapshot.max)
        assertEquals(50.0, snapshot.sum)
    }

    @Test
    fun `HistogramStats handles negative values`() {
        val stats = HistogramStats()
        stats.record(-5.0)
        stats.record(10.0)
        stats.record(-20.0)

        val snapshot = stats.toSnapshot()
        assertEquals(3L, snapshot.count)
        assertEquals(-20.0, snapshot.min)
        assertEquals(10.0, snapshot.max)
        assertEquals(-15.0, snapshot.sum)
    }

    @Test
    fun `HistogramSnapshot toJSONObject serializes correctly`() {
        val snapshot = HistogramSnapshot(count = 5, min = 1.0, max = 10.0, sum = 25.0)
        val json = snapshot.toJSONObject()

        assertEquals(5L, json.getLong("count"))
        assertEquals(1.0, json.getDouble("min"))
        assertEquals(10.0, json.getDouble("max"))
        assertEquals(25.0, json.getDouble("sum"))
    }

    @Test
    fun `HistogramSnapshot fromJSONObject deserializes correctly`() {
        val json =
            JSONObject().apply {
                put("count", 5)
                put("min", 1.0)
                put("max", 10.0)
                put("sum", 25.0)
            }

        val snapshot = HistogramSnapshot.fromJSONObject(json)

        assertEquals(5L, snapshot.count)
        assertEquals(1.0, snapshot.min)
        assertEquals(10.0, snapshot.max)
        assertEquals(25.0, snapshot.sum)
    }

    @Test
    fun `HistogramSnapshot round trips through JSON`() {
        val original = HistogramSnapshot(count = 100, min = 0.5, max = 99.5, sum = 5000.0)
        val json = original.toJSONObject()
        val restored = HistogramSnapshot.fromJSONObject(json)

        assertEquals(original, restored)
    }

    @Test
    fun `HistogramSnapshot toResult calculates average correctly`() {
        val snapshot = HistogramSnapshot(count = 4, min = 5.0, max = 20.0, sum = 50.0)
        val result = snapshot.toResult()

        assertEquals(4L, result.count)
        assertEquals(5.0, result.min)
        assertEquals(20.0, result.max)
        assertEquals(12.5, result.avg)
    }

    @Test
    fun `HistogramSnapshot toResult handles zero count`() {
        val snapshot = HistogramSnapshot(count = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, sum = 0.0)
        val result = snapshot.toResult()

        assertEquals(0L, result.count)
        assertEquals(0.0, result.avg) // Should not divide by zero
    }

    @Test
    fun `HistogramResult toJSONObject serializes correctly`() {
        val result = HistogramResult(count = 10, min = 1.0, max = 100.0, avg = 50.0)
        val json = result.toJSONObject()

        assertEquals(10L, json.getLong("count"))
        assertEquals(1.0, json.getDouble("min"))
        assertEquals(100.0, json.getDouble("max"))
        assertEquals(50.0, json.getDouble("avg"))
    }

    @Test
    fun `HistogramResult fromJSONObject deserializes correctly`() {
        val json =
            JSONObject().apply {
                put("count", 10)
                put("min", 1.0)
                put("max", 100.0)
                put("avg", 50.0)
            }

        val result = HistogramResult.fromJSONObject(json)

        assertEquals(10L, result.count)
        assertEquals(1.0, result.min)
        assertEquals(100.0, result.max)
        assertEquals(50.0, result.avg)
    }

    @Test
    fun `HistogramResult round trips through JSON`() {
        val original = HistogramResult(count = 1000, min = 0.001, max = 999.999, avg = 500.0)
        val json = original.toJSONObject()
        val restored = HistogramResult.fromJSONObject(json)

        assertEquals(original, restored)
    }
}
