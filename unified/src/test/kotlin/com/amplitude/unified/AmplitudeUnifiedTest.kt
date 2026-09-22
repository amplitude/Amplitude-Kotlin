package com.amplitude.unified

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.android.sessionreplay.SessionReplay
import com.amplitude.common.Logger
import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.LoggerProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentClient
import com.amplitude.experiment.ExperimentConfig
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        val amplitude = AmplitudeUnified(builder.buildSnapshot(), recordingFactory(installedNames))

        assertTrue(installedNames.isEmpty())
        assertNull(amplitude.sessionReplay)
        assertNull(amplitude.experiment)
    }

    @Test
    fun `should resolve blade clients from named plugin lookup`() {
        val sessionReplayClient = mockk<SessionReplay>()
        val experimentClient = mockk<ExperimentClient>()
        val sessionReplayPlugin = mockk<SessionReplayPlugin>(relaxed = true)
        val experimentPlugin = mockk<AmplitudeExperimentPlugin>(relaxed = true)
        every { sessionReplayPlugin.name } returns "com.amplitude.android.sessionreplay"
        every { sessionReplayPlugin.sessionReplayClient } returns sessionReplayClient
        every { experimentPlugin.name } returns "com.amplitude.experiment"
        every { experimentPlugin.experimentClient } returns experimentClient

        val amplitude =
            AmplitudeUnified(
                configuration("accessors"),
                object : UnifiedPluginFactory {
                    override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                        sessionReplayPlugin

                    override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin = experimentPlugin

                },
            )

        assertSame(sessionReplayClient, amplitude.sessionReplay)
        assertSame(experimentClient, amplitude.experiment)
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

            }

        val amplitude = AmplitudeUnified(builder.buildSnapshot(), factory)

        assertNull(amplitude.plugin("com.amplitude.android.sessionreplay"))
        assertEquals(
            listOf("com.amplitude.experiment"),
            installedNames,
        )
        assertSame(amplitude, amplitude.track("analytics remains healthy"))
        io.mockk.verify {
            logger.error(match { it.contains("com.amplitude.android.sessionreplay") })
        }
    }

    @Test
    fun `should release blade name after setup fails so it can be added again`() {
        val factory =
            object : UnifiedPluginFactory {
                override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                    ThrowingPlugin("com.amplitude.android.sessionreplay")

                override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin =
                    RecordingPlugin("com.amplitude.experiment", mutableListOf())

            }
        val amplitude = AmplitudeUnified(configuration("setup-failure-name-release"), factory)
        val retryPlugin = RecordingPlugin("com.amplitude.android.sessionreplay", mutableListOf())

        amplitude.add(retryPlugin)

        assertSame(retryPlugin, amplitude.plugin("com.amplitude.android.sessionreplay"))
    }

    @Test
    fun `should not swallow fatal blade errors`() {
        val factory =
            object : UnifiedPluginFactory {
                override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                    FatalPlugin("com.amplitude.android.sessionreplay")

                override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin =
                    RecordingPlugin("com.amplitude.experiment", mutableListOf())

            }

        assertThrows(AssertionError::class.java) {
            AmplitudeUnified(configuration("fatal-error"), factory)
        }
    }

    @Test
    fun `should return null experiment when multiple experiment plugins are installed`() {
        val firstClient = mockk<ExperimentClient>(relaxed = true)
        val secondClient = mockk<ExperimentClient>(relaxed = true)
        val builder = builder("multi-experiment")
        builder.sessionReplay.enabled = false
        val amplitude =
            AmplitudeUnified(
                builder.buildSnapshot(),
                object : UnifiedPluginFactory {
                    override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                        RecordingPlugin("com.amplitude.android.sessionreplay", mutableListOf())

                    override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin =
                        AmplitudeExperimentPlugin(firstClient)

                },
            )

        amplitude.add(AmplitudeExperimentPlugin(secondClient))

        assertNull(amplitude.experiment)
    }

    @Test
    fun `should install real blades through the default plugin factory`() {
        val amplitude =
            AmplitudeUnified("api-key", application) {
                analytics {
                    instanceName = "default-factory"
                    offline = true
                    autocapture = emptySet()
                }
                sessionReplay {
                    autoStart = false
                    enableRemoteConfig = false
                }
                experiment {
                    config = ExperimentConfig.builder().fetchOnStart(false).pollOnStart(false).build()
                }
            }

        assertTrue(amplitude.plugin(SessionReplayPlugin.PLUGIN_NAME) is SessionReplayPlugin)
        assertTrue(amplitude.plugin(AmplitudeExperimentPlugin.PLUGIN_NAME) is AmplitudeExperimentPlugin)
        assertNotNull(amplitude.sessionReplay)
        assertNotNull(amplitude.experiment)
    }

    @Test
    fun `should attribute events to unified during enrichment when library is empty`() {
        val amplitude = disabledAmplitude("attribution")
        val event = BaseEvent().apply { eventType = "attribution" }

        amplitude.timeline.applyPlugins(Plugin.Type.Enrichment, event)

        assertEquals("amplitude-android-unified/${BuildConfig.UNIFIED_VERSION}", event.library)
    }

    @Test
    fun `should prefix an existing analytics library during enrichment`() {
        val amplitude = disabledAmplitude("existing-library")
        val event =
            BaseEvent().apply {
                eventType = "attribution"
                library = "amplitude-android/1.21.1"
            }

        amplitude.timeline.applyPlugins(Plugin.Type.Enrichment, event)

        assertEquals(
            "amplitude-android-unified/${BuildConfig.UNIFIED_VERSION}-amplitude-android/1.21.1",
            event.library,
        )
    }

    @Test
    fun `should not duplicate unified library prefix when already attributed`() {
        val amplitude = disabledAmplitude("idempotent-library")
        val alreadyPrefixed =
            "amplitude-android-unified/${BuildConfig.UNIFIED_VERSION}-amplitude-android/1.21.1"
        val event =
            BaseEvent().apply {
                eventType = "attribution"
                library = alreadyPrefixed
            }

        amplitude.timeline.applyPlugins(Plugin.Type.Enrichment, event)
        amplitude.timeline.applyPlugins(Plugin.Type.Enrichment, event)

        assertEquals(alreadyPrefixed, event.library)
    }

    @Test
    fun `should prefix analytics context library after before plugins`() {
        val amplitude = disabledAmplitude("context-prefix")
        runBlocking { amplitude.isBuilt.await() }
        val event = BaseEvent().apply { eventType = "attribution" }

        amplitude.timeline.applyPlugins(Plugin.Type.Before, event)
        amplitude.timeline.applyPlugins(Plugin.Type.Enrichment, event)

        assertEquals(
            "amplitude-android-unified/${BuildConfig.UNIFIED_VERSION}-" +
                "${AndroidContextPlugin.SDK_LIBRARY}/${AndroidContextPlugin.SDK_VERSION}",
            event.library,
        )
    }

    private fun disabledAmplitude(instanceName: String): AmplitudeUnified {
        val builder = builder(instanceName)
        builder.sessionReplay.enabled = false
        builder.experiment.enabled = false
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
