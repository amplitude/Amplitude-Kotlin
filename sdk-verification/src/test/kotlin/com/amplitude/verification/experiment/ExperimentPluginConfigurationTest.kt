@file:OptIn(com.amplitude.android.GuardedAmplitudeFeature::class)

package com.amplitude.verification.experiment

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.platform.UniversalPlugin
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
class ExperimentPluginConfigurationTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `default deployment uses the Analytics API key`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            try {
                val amplitude = createVerificationAmplitude(application, "default-deployment")
                val plugin =
                    AmplitudeExperimentPlugin(
                        context = application,
                        config = baseConfig(flagsServer),
                    )

                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()
                awaitCondition { flagsServer.requestCount == 1 }

                val request = flagsServer.takeRequest(1, TimeUnit.SECONDS)
                assertEquals("Api-Key $VERIFICATION_API_KEY", request?.getHeader("Authorization"))
                assertSame(plugin.experimentClient, amplitude.experiment)
                assertSame(plugin.experimentClient, amplitude.experiment(null))
                assertNull(amplitude.experiment(VERIFICATION_API_KEY))

                amplitude.remove(plugin as UniversalPlugin)
            } finally {
                flagsServer.shutdown()
            }
        }
    }

    @Test
    fun `explicit deployment key controls requests and keyed lookup`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            try {
                val amplitude = createVerificationAmplitude(application, "explicit-deployment")
                val plugin =
                    AmplitudeExperimentPlugin(
                        context = application,
                        config = baseConfig(flagsServer),
                        deploymentKey = "deployment-key",
                    )

                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()
                awaitCondition { flagsServer.requestCount == 1 }

                val request = flagsServer.takeRequest(1, TimeUnit.SECONDS)
                assertEquals("Api-Key deployment-key", request?.getHeader("Authorization"))
                assertSame(plugin.experimentClient, amplitude.experiment)
                assertSame(plugin.experimentClient, amplitude.experiment("deployment-key"))
                assertNull(amplitude.experiment(null))

                amplitude.remove(plugin as UniversalPlugin)
            } finally {
                flagsServer.shutdown()
            }
        }
    }

    @Test
    fun `fetch on start controls only the initial variants request`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            val automaticServer = variantServer("automatic")
            val manualServer = variantServer("manual")
            try {
                val automaticAmplitude = createVerificationAmplitude(application, "fetch-on-start")
                val automaticPlugin =
                    AmplitudeExperimentPlugin(
                        context = application,
                        config =
                            ExperimentConfig.builder()
                                .serverUrl(automaticServer.url("/").toString())
                                .flagsServerUrl(flagsServer.url("/").toString())
                                .fetchOnStart(true)
                                .pollOnStart(false)
                                .retryFetchOnFailure(false)
                                .automaticExposureTracking(false)
                                .build(),
                    )
                automaticAmplitude.add(automaticPlugin as UniversalPlugin)
                automaticAmplitude.isBuilt.await()
                awaitCondition { automaticPlugin.experimentClient?.variant("checkout")?.value == "automatic" }
                assertEquals(1, automaticServer.requestCount)

                val manualAmplitude = createVerificationAmplitude(application, "manual-fetch")
                val manualPlugin =
                    AmplitudeExperimentPlugin(
                        context = application,
                        config =
                            ExperimentConfig.builder()
                                .serverUrl(manualServer.url("/").toString())
                                .flagsServerUrl(flagsServer.url("/").toString())
                                .fetchOnStart(false)
                                .pollOnStart(false)
                                .retryFetchOnFailure(false)
                                .automaticExposureTracking(false)
                                .build(),
                    )
                manualAmplitude.add(manualPlugin as UniversalPlugin)
                manualAmplitude.isBuilt.await()
                awaitCondition { manualPlugin.experimentClient?.getUser()?.deviceId == "manual-fetch-device" }
                assertEquals(0, manualServer.requestCount)

                manualPlugin.experimentClient?.fetch()?.get(5, TimeUnit.SECONDS)
                assertEquals("manual", manualPlugin.experimentClient?.variant("checkout")?.value)
                assertEquals(1, manualServer.requestCount)

                automaticAmplitude.remove(automaticPlugin as UniversalPlugin)
                manualAmplitude.remove(manualPlugin as UniversalPlugin)
            } finally {
                flagsServer.shutdown()
                automaticServer.shutdown()
                manualServer.shutdown()
            }
        }
    }

    @Test
    fun `custom request headers reach flag and variant endpoints`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            val variantsServer = variantServer("treatment")
            try {
                val amplitude = createVerificationAmplitude(application, "custom-headers")
                val plugin =
                    AmplitudeExperimentPlugin(
                        context = application,
                        config =
                            ExperimentConfig.builder()
                                .serverUrl(variantsServer.url("/").toString())
                                .flagsServerUrl(flagsServer.url("/").toString())
                                .fetchOnStart(true)
                                .pollOnStart(false)
                                .retryFetchOnFailure(false)
                                .customRequestHeaders { mapOf("X-Customer-Header" to "customer-value") }
                                .build(),
                    )
                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()
                awaitCondition { flagsServer.requestCount == 1 && variantsServer.requestCount == 1 }

                assertEquals(
                    "customer-value",
                    flagsServer.takeRequest(1, TimeUnit.SECONDS)?.getHeader("X-Customer-Header"),
                )
                assertEquals(
                    "customer-value",
                    variantsServer.takeRequest(1, TimeUnit.SECONDS)?.getHeader("X-Customer-Header"),
                )

                amplitude.remove(plugin as UniversalPlugin)
            } finally {
                flagsServer.shutdown()
                variantsServer.shutdown()
            }
        }
    }

    @Test
    fun `initial flags provide local evaluation without a variants request`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            val variantsServer = variantServer("remote")
            try {
                val initialFlags =
                    """
                    [
                      {
                        "key":"local-flag",
                        "metadata":{"deployed":true,"evaluationMode":"local","flagType":"release","flagVersion":1},
                        "segments":[{"metadata":{"segmentName":"All Other Users"},"variant":"on"}],
                        "variants":{"off":{"key":"off","metadata":{"default":true}},"on":{"key":"on","value":"on"}}
                      }
                    ]
                    """.trimIndent()
                val amplitude = createVerificationAmplitude(application, "local-evaluation")
                val plugin =
                    AmplitudeExperimentPlugin(
                        context = application,
                        config =
                            ExperimentConfig.builder()
                                .initialFlags(initialFlags)
                                .serverUrl(variantsServer.url("/").toString())
                                .flagsServerUrl(flagsServer.url("/").toString())
                                .fetchOnStart(false)
                                .pollOnStart(false)
                                .retryFetchOnFailure(false)
                                .automaticExposureTracking(false)
                                .build(),
                    )
                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()
                awaitCondition { plugin.experimentClient?.getUser()?.deviceId == "local-evaluation-device" }

                assertEquals("on", plugin.experimentClient?.variant("local-flag")?.value)
                assertEquals(0, variantsServer.requestCount)

                amplitude.remove(plugin as UniversalPlugin)
            } finally {
                flagsServer.shutdown()
                variantsServer.shutdown()
            }
        }
    }

    private fun baseConfig(flagsServer: MockWebServer): ExperimentConfig =
        ExperimentConfig.builder()
            .flagsServerUrl(flagsServer.url("/").toString())
            .fetchOnStart(false)
            .pollOnStart(false)
            .retryFetchOnFailure(false)
            .automaticExposureTracking(false)
            .build()

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
