package com.amplitude.core.utilities

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
        assertEquals("", diagnostics.extractDiagnostics())
        diagnostics.addErrorLog("log")
        diagnostics.addMalformedEvent("event")
        assertEquals("{\"error_logs\":[\"log\"],\"malformed_events\":[\"event\"]}", diagnostics.extractDiagnostics())
        assertFalse(diagnostics.hasDiagnostics())
    }
}
