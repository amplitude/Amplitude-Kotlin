package com.amplitude.verification.combinations

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
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
class BladeCombinationNonAmplitudeHostTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private lateinit var flagsServer: MockWebServer

    @Before
    fun setUp() {
        flagsServer = createMockServer("[]")
    }

    @After
    fun tearDown() {
        flagsServer.shutdown()
    }

    @Test
    fun `Experiment then Session Replay coexist on a third-party host`() {
        verifyCoexistence(addExperimentFirst = true)
    }

    @Test
    fun `Session Replay then Experiment coexist on a third-party host`() {
        verifyCoexistence(addExperimentFirst = false)
    }

    private fun verifyCoexistence(addExperimentFirst: Boolean) {
        runBlocking {
            val host = NonAmplitudeAnalyticsHost(initialSessionId = 1_000L)
            val experimentPlugin = createExperimentPlugin()
            val sessionReplayPlugin = createSessionReplayPlugin()

            if (addExperimentFirst) {
                host.add(experimentPlugin)
                host.add(sessionReplayPlugin)
            } else {
                host.add(sessionReplayPlugin)
                host.add(experimentPlugin)
            }
            shadowOf(Looper.getMainLooper()).idle()
            awaitCondition { host.experiment?.getUser()?.deviceId == "third-party-device" }

            assertNotNull(host.experiment)
            assertNotNull(host.sessionReplay)
            assertEquals("treatment", host.experiment?.variant("checkout")?.value)
            awaitCondition { host.events().any { it.eventType == "\$exposure" } }

            val exposure = host.events().single { it.eventType == "\$exposure" }
            assertEquals(
                "third-party-device/1000",
                exposure.eventProperties?.get("[Amplitude] Session Replay ID"),
            )

            host.updateIdentity(userId = "combined-user", deviceId = "combined-device")
            host.updateSessionId(2_000L)
            awaitCondition {
                host.experiment?.getUser()?.deviceId == "combined-device" &&
                    sessionReplayPlugin.getDeviceId() == "combined-device"
            }
            assertEquals(2_000L, sessionReplayPlugin.getSessionId())

            host.remove(experimentPlugin)
            assertNull(host.experiment)
            assertNotNull(host.sessionReplay)

            host.remove(sessionReplayPlugin)
            assertNull(host.sessionReplay)
        }
    }

    private fun createExperimentPlugin(): AmplitudeExperimentPlugin =
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

    private fun createSessionReplayPlugin(): SessionReplayPlugin =
        SessionReplayPlugin(
            context = application,
            sampleRate = 1.0,
            enableRemoteConfig = false,
            autoStart = true,
        )
}
