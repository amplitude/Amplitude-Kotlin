package com.amplitude.android.crash

import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test

@OptIn(RestrictedAmplitudeFeature::class)
class CrashDiagnosticsTest {
    @Test
    fun `recordCrash uses the correct diagnostics schema`() {
        val diagnosticsClient = mockk<DiagnosticsClient>(relaxed = true)
        val report = "java.lang.IllegalStateException: crash"

        diagnosticsClient.recordCrash(report)

        verify(exactly = 1) { diagnosticsClient.increment("analytics.crash", 1) }
        verify(exactly = 1) {
            diagnosticsClient.recordEvent("analytics.crash", mapOf("report" to report))
        }
    }
}
