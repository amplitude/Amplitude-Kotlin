package com.amplitude.core.utilities

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsTest {
    @Test
    fun `test addMalformedEvent`() {
        val diagnostics = Diagnostics()
        diagnostics.addMalformedEvent("event")
        assertTrue(diagnostics.hasDiagnostics())
        assertEquals("{\"malformed_events\":[\"event\"]}", diagnostics.extractDiagnostics())
    }

    @Test
    fun `test addErrorLog`() {
        val diagnostics = Diagnostics()
        diagnostics.addErrorLog("log")
        assertTrue(diagnostics.hasDiagnostics())
        assertEquals("{\"error_logs\":[\"log\"]}", diagnostics.extractDiagnostics())
    }

    @Test
    fun `test duplicate error logs`() {
        val diagnostics = Diagnostics()
        diagnostics.addErrorLog("log")
        diagnostics.addErrorLog("log")
        assertTrue(diagnostics.hasDiagnostics())
        assertEquals("{\"error_logs\":[\"log\"]}", diagnostics.extractDiagnostics())
    }

    @Test
    fun `test we only take have 10 error logs if many`() {
        val diagnostics = Diagnostics()
        for (i in 1..15) {
            diagnostics.addErrorLog("log$i")
        }
        assertTrue(diagnostics.hasDiagnostics())
        assertEquals(
            "{\"error_logs\":[\"log6\",\"log7\",\"log8\",\"log9\",\"log10\",\"log11\",\"log12\",\"log13\",\"log14\",\"log15\"]}",
            diagnostics.extractDiagnostics(),
        )
        assertFalse(diagnostics.hasDiagnostics())
    }

    @Test
    fun `test we only keep 10 malformed events if many`() {
        val diagnostics = Diagnostics()
        for (i in 1..15) {
            diagnostics.addMalformedEvent("event$i")
        }
        assertEquals(
            "{\"malformed_events\":[\"event6\",\"event7\",\"event8\",\"event9\",\"event10\"," +
                "\"event11\",\"event12\",\"event13\",\"event14\",\"event15\"]}",
            diagnostics.extractDiagnostics(),
        )
        assertFalse(diagnostics.hasDiagnostics())
    }

    @Test
    fun `test long malformed events are truncated`() {
        val diagnostics = Diagnostics()
        diagnostics.addMalformedEvent("x".repeat(5_000))
        val malformedEvent =
            JSONObject(diagnostics.extractDiagnostics()!!)
                .getJSONArray("malformed_events")
                .getString(0)
        assertEquals(1_000, malformedEvent.length)
    }

    @Test
    fun `test hasDiagnostics`() {
        val diagnostics = Diagnostics()
        assertFalse(diagnostics.hasDiagnostics())
        diagnostics.addMalformedEvent("event")
        assertTrue(diagnostics.hasDiagnostics())
        diagnostics.addErrorLog("log")
        assertTrue(diagnostics.hasDiagnostics())
    }

    @Test
    fun `test extractDiagnostics`() {
        val diagnostics = Diagnostics()
        assertEquals(null, diagnostics.extractDiagnostics())
        diagnostics.addErrorLog("log")
        diagnostics.addMalformedEvent("event")
        assertEquals("{\"error_logs\":[\"log\"],\"malformed_events\":[\"event\"]}", diagnostics.extractDiagnostics())
        assertFalse(diagnostics.hasDiagnostics())
    }

    @Test
    fun `should bound diagnostics while writers and extraction run concurrently`() {
        val diagnostics = Diagnostics()
        val workers = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val tasks =
                (0..3).map { worker ->
                    workers.submit {
                        repeat(500) { index ->
                            diagnostics.addMalformedEvent("$worker-$index" + "x".repeat(2_000))
                            diagnostics.addErrorLog("$worker-$index")
                            diagnostics.extractDiagnostics()?.let { payload ->
                                val json = org.json.JSONObject(payload)
                                json.optJSONArray("malformed_events")?.let { events ->
                                    assertTrue(events.length() <= 10)
                                    repeat(events.length()) { assertTrue(events.getString(it).length <= 1_000) }
                                }
                                json.optJSONArray("error_logs")?.let { assertTrue(it.length() <= 10) }
                            }
                        }
                    }
                }
            tasks.forEach { it.get() }
        } finally {
            workers.shutdownNow()
        }
    }
}
