package com.amplitude.unified

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.engagement.engagement
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.android.sessionreplay.SessionReplay
import com.amplitude.common.Logger
import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.LoggerProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.PluginHost
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
internal class AmplitudeUnifiedTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        unmockkStatic("com.amplitude.android.engagement.PluginHostExtensionsKt")
    }

    @Test
    fun `should inherit analytics API and return itself as analytics`() {
        val amplitude =
            AmplitudeUnified("api-key", application) {
                analytics {
                    instanceName = "analytics-surface"
                    offline = true
                    autocapture = emptySet()
                }
                sessionReplay { enabled = false }
                experiment { enabled = false }
                engagement { enabled = false }
            }

        assertSame(amplitude, amplitude.analytics)
        assertSame(amplitude, amplitude.track("inherited track"))
    }

    @Test
    fun `should install enabled blades in deterministic order with stable names`() {
        val installedNames = mutableListOf<String>()
        val factory = recordingFactory(installedNames)

        val amplitude = AmplitudeUnified(configuration("installation-order"), factory)

        assertEquals(
            listOf(
                "com.amplitude.android.sessionreplay",
                "com.amplitude.experiment",
                "com.amplitude.android.engagement",
            ),
            installedNames,
        )
        installedNames.forEach { name -> assertEquals(name, amplitude.plugin(name)?.name) }
    }

    @Test
    fun `should skip disabled blades and return null accessors`() {
        val installedNames = mutableListOf<String>()
        val builder = builder("disabled")
        builder.sessionReplay.enabled = false
        builder.experiment.enabled = false
        builder.engagement.enabled = false

        val amplitude = AmplitudeUnified(builder.buildSnapshot(), recordingFactory(installedNames))

        assertTrue(installedNames.isEmpty())
        assertNull(amplitude.sessionReplay)
        assertNull(amplitude.experiment)
        assertNull(amplitude.engagement)
    }

    @Test
    fun `should resolve blade clients from named plugin lookup`() {
        val sessionReplayClient = mockk<SessionReplay>()
        val experimentClient = mockk<ExperimentClient>()
        val sessionReplayPlugin = mockk<SessionReplayPlugin>(relaxed = true)
        val experimentPlugin = mockk<AmplitudeExperimentPlugin>(relaxed = true)
        val engagementPlugin = RecordingPlugin("com.amplitude.android.engagement", mutableListOf())
        val engagementClient = mockk<com.amplitude.android.engagement.AmplitudeEngagementPlugin>()
        every { sessionReplayPlugin.name } returns "com.amplitude.android.sessionreplay"
        every { sessionReplayPlugin.sessionReplayClient } returns sessionReplayClient
        every { experimentPlugin.name } returns "com.amplitude.experiment"
        every { experimentPlugin.experimentClient } returns experimentClient
        mockkStatic("com.amplitude.android.engagement.PluginHostExtensionsKt")
        every { any<PluginHost>().engagement } returns engagementClient

        val amplitude =
            AmplitudeUnified(
                configuration("accessors"),
                object : UnifiedPluginFactory {
                    override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                        sessionReplayPlugin

                    override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin = experimentPlugin

                    override fun engagement(configuration: EngagementConfiguration): UniversalPlugin = engagementPlugin
                },
            )

        assertSame(sessionReplayClient, amplitude.sessionReplay)
        assertSame(experimentClient, amplitude.experiment)
        assertSame(engagementClient, amplitude.engagement)
    }

    @Test
    fun `should continue after one blade setup fails and log through analytics`() {
        val installedNames = mutableListOf<String>()
        val logger = mockk<Logger>(relaxed = true)
        val builder = builder("failure-isolation")
        builder.analytics.loggerProvider =
            object : LoggerProvider {
                override fun getLogger(amplitude: com.amplitude.core.Amplitude): Logger = logger
            }
        val factory =
            object : UnifiedPluginFactory {
                override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                    ThrowingPlugin("com.amplitude.android.sessionreplay")

                override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin =
                    RecordingPlugin("com.amplitude.experiment", installedNames)

                override fun engagement(configuration: EngagementConfiguration): UniversalPlugin =
                    RecordingPlugin("com.amplitude.android.engagement", installedNames)
            }

        val amplitude = AmplitudeUnified(builder.buildSnapshot(), factory)

        assertNull(amplitude.plugin("com.amplitude.android.sessionreplay"))
        assertEquals(
            listOf("com.amplitude.experiment", "com.amplitude.android.engagement"),
            installedNames,
        )
        assertSame(amplitude, amplitude.track("analytics remains healthy"))
        io.mockk.verify {
            logger.error(match { it.contains("com.amplitude.android.sessionreplay") })
        }
    }

    @Test
    fun `should not swallow fatal blade errors`() {
        val factory =
            object : UnifiedPluginFactory {
                override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                    FatalPlugin("com.amplitude.android.sessionreplay")

                override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin =
                    RecordingPlugin("com.amplitude.experiment", mutableListOf())

                override fun engagement(configuration: EngagementConfiguration): UniversalPlugin =
                    RecordingPlugin("com.amplitude.android.engagement", mutableListOf())
            }

        assertThrows(AssertionError::class.java) {
            AmplitudeUnified(configuration("fatal-error"), factory)
        }
    }

    @Test
    fun `should attribute events to unified before analytics context`() {
        val amplitude = disabledAmplitude("attribution")
        val event = BaseEvent().apply { eventType = "attribution" }

        amplitude.timeline.applyPlugins(Plugin.Type.Before, event)

        assertEquals("amplitude-unified-android/1.30.1", event.library)
    }

    private fun disabledAmplitude(instanceName: String): AmplitudeUnified {
        val builder = builder(instanceName)
        builder.sessionReplay.enabled = false
        builder.experiment.enabled = false
        builder.engagement.enabled = false
        return AmplitudeUnified(builder)
    }

    private fun configuration(instanceName: String): UnifiedConfiguration = builder(instanceName).buildSnapshot()

    private fun builder(instanceName: String): UnifiedConfigurationBuilder =
        UnifiedConfigurationBuilder("api-key", application).apply {
            analytics {
                this.instanceName = instanceName
                offline = true
                autocapture = emptySet()
            }
        }

    private fun recordingFactory(installedNames: MutableList<String>): UnifiedPluginFactory =
        object : UnifiedPluginFactory {
            override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                RecordingPlugin("com.amplitude.android.sessionreplay", installedNames)

            override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin =
                RecordingPlugin("com.amplitude.experiment", installedNames)

            override fun engagement(configuration: EngagementConfiguration): UniversalPlugin =
                RecordingPlugin("com.amplitude.android.engagement", installedNames)
        }
}

private open class RecordingPlugin(
    override val name: String,
    private val installedNames: MutableList<String>,
) : UniversalPlugin {
    override fun setup(
        client: AnalyticsClient,
        context: AmplitudeContext,
    ) {
        installedNames.add(name)
    }
}

private class ThrowingPlugin(name: String) : RecordingPlugin(name, mutableListOf()) {
    override fun setup(
        client: AnalyticsClient,
        context: AmplitudeContext,
    ) {
        error("setup failed")
    }
}

private class FatalPlugin(name: String) : RecordingPlugin(name, mutableListOf()) {
    override fun setup(
        client: AnalyticsClient,
        context: AmplitudeContext,
    ) {
        throw AssertionError("fatal setup failure")
    }
}
