package com.amplitude.core.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsSnapshotTest {
    @Test
    fun `isEmpty returns true when counters histograms and events are all empty`() {
        val snapshot =
            DiagnosticsSnapshot(
                tags = mapOf("tag" to "value"),
                counters = emptyMap(),
                histograms = emptyMap(),
                events = emptyList(),
            )
        assertTrue(snapshot.isEmpty())
    }

    @Test
    fun `isEmpty returns true when counters histograms and events are null`() {
        val snapshot =
            DiagnosticsSnapshot(
                tags = mapOf("tag" to "value"),
                counters = null,
                histograms = null,
                events = null,
            )
        assertTrue(snapshot.isEmpty())
    }

    @Test
    fun `isEmpty returns false when counters has data`() {
        val snapshot =
            DiagnosticsSnapshot(
                tags = null,
                counters = mapOf("counter" to 1L),
                histograms = null,
                events = null,
            )
        assertFalse(snapshot.isEmpty())
    }

    @Test
    fun `isEmpty returns false when histograms has data`() {
        val snapshot =
            DiagnosticsSnapshot(
                tags = null,
                counters = null,
                histograms = mapOf("histogram" to HistogramSnapshot(count = 1, min = 1.0, max = 1.0, sum = 1.0)),
                events = null,
            )
        assertFalse(snapshot.isEmpty())
    }

    @Test
    fun `isEmpty returns false when events has data`() {
        val snapshot =
            DiagnosticsSnapshot(
                tags = null,
                counters = null,
                histograms = null,
                events = listOf(DiagnosticsEvent("event", 123.0, null)),
            )
        assertFalse(snapshot.isEmpty())
    }

    @Test
    fun `empty factory method creates empty snapshot`() {
        val snapshot = DiagnosticsSnapshot.empty()

        assertEquals(emptyMap<String, String>(), snapshot.tags)
        assertEquals(emptyMap<String, Long>(), snapshot.counters)
        assertEquals(emptyMap<String, HistogramSnapshot>(), snapshot.histograms)
        assertEquals(emptyList<DiagnosticsEvent>(), snapshot.events)
    }

    @Test
    fun `toPayload converts snapshot to payload`() {
        val snapshot =
            DiagnosticsSnapshot(
                tags = mapOf("tag" to "value"),
                counters = mapOf("counter" to 5L),
                histograms = mapOf("metric" to HistogramSnapshot(count = 2, min = 1.0, max = 3.0, sum = 4.0)),
                events = listOf(DiagnosticsEvent("event", 100.0, mapOf("k" to "v"))),
            )

        val payload = snapshot.toPayload()

        assertEquals(mapOf("tag" to "value"), payload.tags)
        assertEquals(mapOf("counter" to 5L), payload.counters)
        assertNotNull(payload.histograms)
        assertEquals(1, payload.histograms?.size)
        val histogramResult = payload.histograms?.get("metric")
        assertNotNull(histogramResult)
        assertEquals(2L, histogramResult?.count)
        assertEquals(1.0, histogramResult?.min)
        assertEquals(3.0, histogramResult?.max)
        assertEquals(2.0, histogramResult?.avg) // 4.0 / 2 = 2.0
        assertEquals(1, payload.events?.size)
    }

    @Test
    fun `toPayload handles null fields`() {
        val snapshot =
            DiagnosticsSnapshot(
                tags = null,
                counters = null,
                histograms = null,
                events = null,
            )

        val payload = snapshot.toPayload()

        assertNull(payload.tags)
        assertNull(payload.counters)
        assertNull(payload.histograms)
        assertNull(payload.events)
    }

    @Test
    fun `toPayload converts histogram snapshot to result with calculated average`() {
        val snapshot =
            DiagnosticsSnapshot(
                tags = null,
                counters = null,
                histograms =
                    mapOf(
                        "metric1" to HistogramSnapshot(count = 4, min = 10.0, max = 40.0, sum = 100.0),
                        "metric2" to HistogramSnapshot(count = 1, min = 5.0, max = 5.0, sum = 5.0),
                    ),
                events = null,
            )

        val payload = snapshot.toPayload()

        val metric1 = payload.histograms?.get("metric1")
        assertNotNull(metric1)
        assertEquals(25.0, metric1?.avg) // 100 / 4 = 25

        val metric2 = payload.histograms?.get("metric2")
        assertNotNull(metric2)
        assertEquals(5.0, metric2?.avg) // 5 / 1 = 5
    }
}
