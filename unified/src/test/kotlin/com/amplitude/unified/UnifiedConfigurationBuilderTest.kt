package com.amplitude.unified

import android.content.Context
import com.amplitude.android.sessionreplay.config.MaskLevel
import com.amplitude.android.sessionreplay.config.PrivacyConfig
import com.amplitude.core.ServerZone
import com.amplitude.experiment.ExperimentConfig
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class UnifiedConfigurationBuilderTest {
    private val context: Context = mockk(relaxed = true)

    @Nested
    inner class NestedBuilders {
        @Test
        fun `should configure analytics and every blade from nested builders`() {
            val experimentConfig = ExperimentConfig.builder().fetchOnStart(false).build()
            val builder = UnifiedConfigurationBuilder("api-key", context)

            builder.analytics {
                instanceName = "unified"
                serverZone = ServerZone.EU
            }
            builder.sessionReplay {
                enabled = false
                sampleRate = 0.5
                privacyConfig = PrivacyConfig(MaskLevel.LIGHT)
            }
            builder.experiment {
                enabled = false
                deploymentKey = "experiment-deployment-key"
                config = experimentConfig
            }

            val configuration = builder.buildSnapshot()

            assertEquals("api-key", configuration.analytics.apiKey)
            assertEquals("unified", configuration.analytics.instanceName)
            assertEquals(ServerZone.EU, configuration.analytics.serverZone)
            assertFalse(configuration.sessionReplay.enabled)
            assertEquals(0.5, configuration.sessionReplay.sampleRate)
            assertEquals(MaskLevel.LIGHT, configuration.sessionReplay.privacyConfig.maskLevel)
            assertFalse(configuration.experiment.enabled)
            assertEquals("experiment-deployment-key", configuration.experiment.deploymentKey)
            assertEquals(experimentConfig, configuration.experiment.config)
        }
    }

    @Nested
    inner class Defaults {
        @Test
        fun `should enable blades by default with safe session replay sampling`() {
            val configuration = UnifiedConfigurationBuilder("api-key", context).buildSnapshot()

            assertTrue(configuration.sessionReplay.enabled)
            assertEquals(0.0, configuration.sessionReplay.sampleRate)
            assertTrue(configuration.experiment.enabled)
            assertNull(configuration.experiment.deploymentKey)
        }
    }

    @Nested
    inner class SnapshotIsolation {
        @Test
        fun `should isolate built configuration from later builder changes`() {
            val builder = UnifiedConfigurationBuilder("api-key", context)
            builder.analytics.instanceName = "before"
            builder.sessionReplay.enabled = true

            val configuration = builder.buildSnapshot()
            builder.analytics.instanceName = "after"
            builder.sessionReplay.enabled = false

            assertEquals("before", configuration.analytics.instanceName)
            assertTrue(configuration.sessionReplay.enabled)
        }
    }
}
