package com.amplitude.unified

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.sessionreplay.config.MaskLevel
import com.amplitude.android.sessionreplay.config.PrivacyConfig
import com.amplitude.core.ServerZone
import com.amplitude.experiment.ExperimentConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
internal class UnifiedConfigurationBuilderTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `should configure analytics and every blade from nested builders`() {
        val experimentConfig = ExperimentConfig.builder().fetchOnStart(false).build()
        val builder = UnifiedConfigurationBuilder("api-key", application)

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
            config = experimentConfig
        }
        builder.engagement {
            enabled = false
        }

        val configuration = builder.buildSnapshot()

        assertEquals("api-key", configuration.analytics.apiKey)
        assertEquals("unified", configuration.analytics.instanceName)
        assertEquals(ServerZone.EU, configuration.analytics.serverZone)
        assertFalse(configuration.sessionReplay.enabled)
        assertEquals(0.5, configuration.sessionReplay.sampleRate)
        assertEquals(MaskLevel.LIGHT, configuration.sessionReplay.privacyConfig.maskLevel)
        assertFalse(configuration.experiment.enabled)
        assertEquals(experimentConfig, configuration.experiment.config)
        assertFalse(configuration.engagement.enabled)
    }

    @Test
    fun `should enable blades by default with safe session replay sampling`() {
        val configuration = UnifiedConfigurationBuilder("api-key", application).buildSnapshot()

        assertTrue(configuration.sessionReplay.enabled)
        assertEquals(0.0, configuration.sessionReplay.sampleRate)
        assertTrue(configuration.experiment.enabled)
        assertTrue(configuration.engagement.enabled)
    }

    @Test
    fun `should isolate built configuration from later builder changes`() {
        val builder = UnifiedConfigurationBuilder("api-key", application)
        builder.analytics.instanceName = "before"
        builder.sessionReplay.enabled = true
        builder.engagement.options.serverUrl = "https://before.example.com"

        val configuration = builder.buildSnapshot()
        builder.analytics.instanceName = "after"
        builder.sessionReplay.enabled = false
        builder.engagement.options.serverUrl = "https://after.example.com"

        assertEquals("before", configuration.analytics.instanceName)
        assertTrue(configuration.sessionReplay.enabled)
        assertEquals("https://before.example.com", configuration.engagement.options.serverUrl)
    }
}
