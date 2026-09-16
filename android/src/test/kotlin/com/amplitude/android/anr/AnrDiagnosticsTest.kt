package com.amplitude.android.anr

import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

@OptIn(RestrictedAmplitudeFeature::class)
class AnrDiagnosticsTest {
    @Test
    fun `recordAnr uses the correct diagnostics schema`() {
        val diagnosticsClient = mockk<DiagnosticsClient>(relaxed = true)
        val report = "ANR detected"

        diagnosticsClient.recordAnr(report)

        verify(exactly = 1) { diagnosticsClient.increment("analytics.anr", 1) }
        verify(exactly = 1) {
            diagnosticsClient.recordEvent("analytics.anr", mapOf("report" to report))
        }
    }
}
