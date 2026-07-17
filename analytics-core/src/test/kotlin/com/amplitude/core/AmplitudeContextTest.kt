package com.amplitude.core

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.remoteconfig.RemoteConfigClient
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@OptIn(RestrictedAmplitudeFeature::class)
class AmplitudeContextTest {
    @Test
    fun `restricted clients initialize lazily once`() {
        val remoteConfigClient = mockk<RemoteConfigClient>()
        val diagnosticsClient = mockk<DiagnosticsClient>()
        var remoteConfigInitializations = 0
        var diagnosticsInitializations = 0
        val context =
            AmplitudeContext(
                apiKey = "api-key",
                instanceName = "instance",
                serverZone = ServerZone.US,
                logger = ConsoleLogger.logger,
                remoteConfigClientProvider = {
                    remoteConfigInitializations += 1
                    remoteConfigClient
                },
                diagnosticsClientProvider = {
                    diagnosticsInitializations += 1
                    diagnosticsClient
                },
            )

        assertEquals(0, remoteConfigInitializations)
        assertEquals(0, diagnosticsInitializations)

        assertSame(remoteConfigClient, context.remoteConfigClient)
        assertSame(remoteConfigClient, context.remoteConfigClient)
        assertSame(diagnosticsClient, context.diagnosticsClient)
        assertSame(diagnosticsClient, context.diagnosticsClient)

        assertEquals(1, remoteConfigInitializations)
        assertEquals(1, diagnosticsInitializations)
    }
}
