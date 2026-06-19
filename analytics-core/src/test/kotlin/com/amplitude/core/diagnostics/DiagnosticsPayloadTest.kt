package com.amplitude.core.diagnostics

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsPayloadTest {
    @Test
    fun `toJSONObject includes all fields when present`() {
        val payload =
            DiagnosticsPayload(
                tags = mapOf("tag1" to "value1", "tag2" to "value2"),
                counters = mapOf("counter1" to 10L, "counter2" to 20L),
                histograms = mapOf("metric" to HistogramResult(count = 5, min = 1.0, max = 10.0, avg = 5.0)),
                events = listOf(DiagnosticsEvent("event", 123.0, mapOf("k" to "v"))),
            )

        val json = payload.toJSONObject()

        // Verify tags
        assertTrue(json.has("tags"))
        val tags = json.getJSONObject("tags")
        assertEquals("value1", tags.getString("tag1"))
        assertEquals("value2", tags.getString("tag2"))

        // Verify counters
        assertTrue(json.has("counters"))
        val counters = json.getJSONObject("counters")
        assertEquals(10L, counters.getLong("counter1"))
        assertEquals(20L, counters.getLong("counter2"))

        // Verify histogram (note: key is "histogram" not "histograms")
        assertTrue(json.has("histogram"))
        val histogram = json.getJSONObject("histogram")
        val metric = histogram.getJSONObject("metric")
        assertEquals(5L, metric.getLong("count"))
        assertEquals(1.0, metric.getDouble("min"))
        assertEquals(10.0, metric.getDouble("max"))
        assertEquals(5.0, metric.getDouble("avg"))

        // Verify events
        assertTrue(json.has("events"))
        val events = json.getJSONArray("events")
        assertEquals(1, events.length())
        val event = events.getJSONObject(0)
        assertEquals("event", event.getString("event_name"))
    }

    @Test
    fun `toJSONObject excludes null tags`() {
        val payload =
            DiagnosticsPayload(
                tags = null,
                counters = mapOf("counter" to 1L),
                histograms = null,
                events = null,
            )

        val json = payload.toJSONObject()

        assertFalse(json.has("tags"))
        assertTrue(json.has("counters"))
    }

    @Test
    fun `toJSONObject excludes null counters`() {
        val payload =
            DiagnosticsPayload(
                tags = mapOf("tag" to "value"),
                counters = null,
                histograms = null,
                events = null,
            )

        val json = payload.toJSONObject()

        assertTrue(json.has("tags"))
        assertFalse(json.has("counters"))
    }

    @Test
    fun `toJSONObject excludes null histograms`() {
        val payload =
            DiagnosticsPayload(
                tags = null,
                counters = mapOf("counter" to 1L),
                histograms = null,
                events = null,
            )

        val json = payload.toJSONObject()

        assertFalse(json.has("histogram"))
    }

    @Test
    fun `toJSONObject excludes null events`() {
        val payload =
            DiagnosticsPayload(
                tags = null,
                counters = mapOf("counter" to 1L),
                histograms = null,
                events = null,
            )

        val json = payload.toJSONObject()

        assertFalse(json.has("events"))
    }

    @Test
    fun `toJSONObject handles all null fields`() {
        val payload =
            DiagnosticsPayload(
                tags = null,
                counters = null,
                histograms = null,
                events = null,
            )

        val json = payload.toJSONObject()

        assertFalse(json.has("tags"))
        assertFalse(json.has("counters"))
        assertFalse(json.has("histogram"))
        assertFalse(json.has("events"))
        assertEquals("{}", json.toString())
    }

    @Test
    fun `toJsonString returns valid JSON string`() {
        val payload =
            DiagnosticsPayload(
                tags = mapOf("tag" to "value"),
                counters = mapOf("counter" to 42L),
                histograms = null,
                events = null,
            )

        val jsonString = payload.toJsonString()

        // Verify it can be parsed back
        val parsed = JSONObject(jsonString)
        assertEquals("value", parsed.getJSONObject("tags").getString("tag"))
        assertEquals(42L, parsed.getJSONObject("counters").getLong("counter"))
    }

    @Test
    fun `toJSONObject handles multiple events`() {
        val payload =
            DiagnosticsPayload(
                tags = null,
                counters = null,
                histograms = null,
                events =
                    listOf(
                        DiagnosticsEvent("event1", 100.0, null),
                        DiagnosticsEvent("event2", 200.0, mapOf("prop" to "value")),
                        DiagnosticsEvent("event3", 300.0, null),
                    ),
            )

        val json = payload.toJSONObject()

        assertTrue(json.has("events"))
        val events = json.getJSONArray("events")
        assertEquals(3, events.length())
        assertEquals("event1", events.getJSONObject(0).getString("event_name"))
        assertEquals("event2", events.getJSONObject(1).getString("event_name"))
        assertEquals("event3", events.getJSONObject(2).getString("event_name"))
    }

    @Test
    fun `toJSONObject handles multiple histograms`() {
        val payload =
            DiagnosticsPayload(
                tags = null,
                counters = null,
                histograms =
                    mapOf(
                        "latency" to HistogramResult(count = 100, min = 10.0, max = 500.0, avg = 150.0),
                        "size" to HistogramResult(count = 50, min = 1024.0, max = 10240.0, avg = 5000.0),
                    ),
                events = null,
            )

        val json = payload.toJSONObject()

        assertTrue(json.has("histogram"))
        val histograms = json.getJSONObject("histogram")
        assertTrue(histograms.has("latency"))
        assertTrue(histograms.has("size"))
        assertEquals(100L, histograms.getJSONObject("latency").getLong("count"))
        assertEquals(50L, histograms.getJSONObject("size").getLong("count"))
    }
}
