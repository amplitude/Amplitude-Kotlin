package com.amplitude.verification.experiment

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.experiment.Source
import com.amplitude.experiment.Variant
import com.amplitude.experiment.experiment
import com.amplitude.verification.support.NonAmplitudeAnalyticsHost
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExperimentPluginNonAmplitudeHostTest {
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
    fun `third-party host supplies identity session and exposure tracking`() {
        runBlocking {
            val host =
                NonAmplitudeAnalyticsHost(
                    initialUserProperties = mapOf("plan" to "free"),
                    initialSessionId = 1_000L,
                )
            val plugin =
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

            host.add(plugin)
            awaitCondition { host.experiment?.getUser()?.deviceId == "third-party-device" }

            assertEquals("treatment", host.experiment?.variant("checkout")?.value)
            awaitCondition { host.events().count { it.eventType == "\$exposure" } == 1 }

            host.updateIdentity(
                userId = "updated-user",
                deviceId = "updated-device",
                userProperties = mapOf("plan" to "pro"),
            )
            host.updateSessionId(2_000L)
            awaitCondition {
                val user = host.experiment?.getUser()
                user?.userId == "updated-user" &&
                    user.deviceId == "updated-device" &&
                    user.userProperties?.get("plan") == "pro" &&
                    user.userProperties?.get("session_id") == 2_000L
            }

            val exposure = host.events().single { it.eventType == "\$exposure" }
            assertEquals("checkout", exposure.eventProperties?.get("flag_key"))
            assertEquals("treatment", exposure.eventProperties?.get("variant"))

            host.remove(plugin)
            assertNull(host.experiment)
        }
    }
}
