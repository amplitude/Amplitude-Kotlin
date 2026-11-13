@file:OptIn(com.amplitude.android.GuardedAmplitudeFeature::class)

package com.amplitude.verification.experiment

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.platform.plugins
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.experiment.experiment
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExperimentPluginMultipleInstancesTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `different deployment keys remain independently addressable and isolated`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            val firstServer = variantServer("first")
            val secondServer = variantServer("second")
            try {
                val amplitude = createVerificationAmplitude(application, "multiple-deployments")
                val firstPlugin = createPlugin("deployment-first", firstServer, flagsServer)
                val secondPlugin = createPlugin("deployment-second", secondServer, flagsServer)

                amplitude.add(firstPlugin as UniversalPlugin)
                amplitude.add(secondPlugin as UniversalPlugin)
                amplitude.isBuilt.await()
                awaitCondition {
                    firstPlugin.experimentClient?.getUser()?.deviceId == "multiple-deployments-device" &&
                        secondPlugin.experimentClient?.getUser()?.deviceId == "multiple-deployments-device"
                }

                assertNull(amplitude.experiment)
                assertSame(firstPlugin.experimentClient, amplitude.experiment("deployment-first"))
                assertSame(secondPlugin.experimentClient, amplitude.experiment("deployment-second"))

                firstPlugin.experimentClient?.fetch()?.get(5, TimeUnit.SECONDS)
                secondPlugin.experimentClient?.fetch()?.get(5, TimeUnit.SECONDS)

                assertEquals("first", amplitude.experiment("deployment-first")?.variant("checkout")?.value)
                assertEquals("second", amplitude.experiment("deployment-second")?.variant("checkout")?.value)

                amplitude.experiment("deployment-first")?.clear()
                assertNull(amplitude.experiment("deployment-first")?.variant("checkout")?.value)
                assertEquals("second", amplitude.experiment("deployment-second")?.variant("checkout")?.value)

                amplitude.remove(firstPlugin as UniversalPlugin)
                amplitude.remove(secondPlugin as UniversalPlugin)
            } finally {
                flagsServer.shutdown()
                firstServer.shutdown()
                secondServer.shutdown()
            }
        }
    }

    private fun createPlugin(
        deploymentKey: String,
        variantsServer: MockWebServer,
        flagsServer: MockWebServer,
    ): AmplitudeExperimentPlugin =
        AmplitudeExperimentPlugin(
            context = application,
            config =
                ExperimentConfig.builder()
                    .serverUrl(variantsServer.url("/").toString())
                    .flagsServerUrl(flagsServer.url("/").toString())
                    .fetchOnStart(false)
                    .pollOnStart(false)
                    .retryFetchOnFailure(false)
                    .automaticExposureTracking(false)
                    .build(),
            deploymentKey = deploymentKey,
        )

    private fun variantServer(value: String): MockWebServer =
        MockWebServer().apply {
            enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"checkout":{"key":"$value","value":"$value"}}"""),
            )
            start()
        }
}
