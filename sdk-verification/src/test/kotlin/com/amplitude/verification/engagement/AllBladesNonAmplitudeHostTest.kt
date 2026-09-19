package com.amplitude.verification.engagement

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.engagement.AmplitudeEngagementPluginFactory
import com.amplitude.android.engagement.engagement
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.android.plugins.sessionReplay
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.experiment.Source
import com.amplitude.experiment.Variant
import com.amplitude.experiment.experiment
import com.amplitude.verification.experiment.awaitCondition
import com.amplitude.verification.experiment.createMockServer
import com.amplitude.verification.support.NonAmplitudeAnalyticsHost
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AllBladesNonAmplitudeHostTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private lateinit var engagementServer: MockWebServer
    private lateinit var flagsServer: MockWebServer

    @Before
    fun setUp() {
        engagementServer = MockWebServer()
        engagementServer.respondToEngagementRequests()
        engagementServer.start()
        flagsServer = createMockServer("[]")
    }

    @After
    fun tearDown() {
        engagementServer.shutdown()
        flagsServer.shutdown()
    }

    @Test
    fun `all blades coexist and can be removed independently on a third-party host`() {
        runBlocking {
            val host = NonAmplitudeAnalyticsHost(initialSessionId = 1_000L)
            val engagementPlugin =
                AmplitudeEngagementPluginFactory.make(application, engagementServer.engagementOptions())
            val experimentPlugin =
                AmplitudeExperimentPlugin(
                    context = application,
                    config =
                        ExperimentConfig.builder()
                            .initialVariants(mapOf("checkout" to Variant("treatment")))
                            .source(Source.INITIAL_VARIANTS)
                            .flagsServerUrl(flagsServer.url("/").toString())
                            .automaticExposureTracking(true)
                            .fetchOnStart(false)
                            .pollOnStart(false)
                            .build(),
                )
            val sessionReplayPlugin =
                SessionReplayPlugin(
                    context = application,
                    sampleRate = 1.0,
                    enableRemoteConfig = false,
                    autoStart = true,
                )

            host.add(engagementPlugin)
            host.add(experimentPlugin)
            host.add(sessionReplayPlugin)
            shadowOf(Looper.getMainLooper()).idle()
            awaitEngagement(host)
            awaitCondition { host.experiment?.getUser()?.deviceId == "third-party-device" }

            assertNotNull(host.engagement)
            assertNotNull(host.experiment)
            assertNotNull(host.sessionReplay)

            assertEquals("treatment", host.experiment?.variant("checkout")?.value)
            awaitCondition { host.events().any { it.eventType == "\$exposure" } }
            val exposure = host.events().single { it.eventType == "\$exposure" }
            assertEquals(
                "third-party-device/1000",
                exposure.eventProperties?.get("[Amplitude] Session Replay ID"),
            )

            host.remove(sessionReplayPlugin)
            assertNull(host.sessionReplay)
            assertNotNull(host.experiment)
            assertNotNull(host.engagement)

            host.remove(experimentPlugin)
            assertNull(host.experiment)
            assertNotNull(host.engagement)

            host.remove(engagementPlugin)
            awaitNoEngagement(host)
            assertNull(host.engagement)
        }
    }
}
