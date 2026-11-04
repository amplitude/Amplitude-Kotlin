@file:OptIn(com.amplitude.android.GuardedAmplitudeFeature::class)

package com.amplitude.verification.experiment

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExperimentPluginLifecycleTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `opted out registration waits until opt in before starting Experiment`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            val variantsServer = variantServer("treatment")
            try {
                val amplitude = createVerificationAmplitude(application, "starts-opted-out", optOut = true)
                val plugin = createPlugin(flagsServer, variantsServer, fetchOnStart = true)

                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()

                assertNull(flagsServer.takeRequest(250, TimeUnit.MILLISECONDS))
                assertNull(variantsServer.takeRequest(250, TimeUnit.MILLISECONDS))

                amplitude.optOut = false
                awaitCondition {
                    flagsServer.requestCount == 1 &&
                        plugin.experimentClient?.variant("checkout")?.value == "treatment"
                }
                assertEquals(1, variantsServer.requestCount)

                amplitude.remove(plugin as UniversalPlugin)
            } finally {
                flagsServer.shutdown()
                variantsServer.shutdown()
            }
        }
    }

    @Test
    fun `reset updates Experiment identity without implicitly clearing Analytics user properties`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            val variantsServer = variantServer("treatment")
            try {
                val amplitude = createVerificationAmplitude(application, "reset-properties", deviceId = null)
                val plugin =
                    createPlugin(
                        flagsServer,
                        variantsServer,
                        fetchOnStart = false,
                        automaticIdentityFetch = true,
                    )
                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()
                awaitCondition {
                    amplitude.getDeviceId() != null &&
                        plugin.experimentClient?.getUser()?.deviceId == amplitude.getDeviceId()
                }

                amplitude.identify(mapOf("plan" to "pro"))
                awaitCondition { plugin.experimentClient?.getUser()?.userProperties?.get("plan") == "pro" }
                val previousDeviceId = amplitude.getDeviceId()

                amplitude.reset()
                awaitCondition {
                    amplitude.getDeviceId() != previousDeviceId &&
                        plugin.experimentClient?.getUser()?.deviceId == amplitude.getDeviceId()
                }

                assertNotEquals(previousDeviceId, amplitude.getDeviceId())
                assertEquals("pro", plugin.experimentClient?.getUser()?.userProperties?.get("plan"))
                amplitude.remove(plugin as UniversalPlugin)
            } finally {
                flagsServer.shutdown()
                variantsServer.shutdown()
            }
        }
    }

    private fun createPlugin(
        flagsServer: MockWebServer,
        variantsServer: MockWebServer,
        fetchOnStart: Boolean,
        automaticIdentityFetch: Boolean = false,
    ): AmplitudeExperimentPlugin =
        AmplitudeExperimentPlugin(
            context = application,
            config =
                ExperimentConfig.builder()
                    .serverUrl(variantsServer.url("/").toString())
                    .flagsServerUrl(flagsServer.url("/").toString())
                    .fetchOnStart(fetchOnStart)
                    .pollOnStart(false)
                    .retryFetchOnFailure(false)
                    .automaticExposureTracking(false)
                    .automaticFetchOnAmplitudeIdentityChange(automaticIdentityFetch)
                    .build(),
        )

    private fun variantServer(value: String): MockWebServer =
        createMockServer("""{"checkout":{"key":"$value","value":"$value"}}""")

}
