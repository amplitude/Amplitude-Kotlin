package com.amplitude.verification.unified

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.experiment.Source
import com.amplitude.experiment.Variant
import com.amplitude.unified.AmplitudeUnified
import com.amplitude.verification.experiment.awaitCondition
import com.amplitude.verification.experiment.createMockServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UnifiedWrapperIntegrationTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `separate Experiment deployment key authenticates requests`() {
        runBlocking {
            val flagsServer = createMockServer("{}")
            try {
                val amplitude =
                    AmplitudeUnified("analytics-api-key", application) {
                        analytics {
                            instanceName = uniqueInstanceName("experiment-deployment-key")
                            offline = true
                            autocapture = emptySet()
                        }
                        sessionReplay { enabled = false }
                        experiment {
                            deploymentKey = "experiment-deployment-key"
                            config =
                                ExperimentConfig.builder()
                                    .serverUrl(flagsServer.url("/").toString())
                                    .flagsServerUrl(flagsServer.url("/").toString())
                                    .fetchOnStart(false)
                                    .pollOnStart(false)
                                    .retryFetchOnFailure(false)
                                    .build()
                        }
                    }

                amplitude.isBuilt.await()
                assertNotNull(amplitude.experiment)
                amplitude.experiment?.fetch()?.get(5, TimeUnit.SECONDS)

                val request = flagsServer.takeRequest(5, TimeUnit.SECONDS)
                assertEquals("Api-Key experiment-deployment-key", request?.getHeader("Authorization"))
            } finally {
                flagsServer.shutdown()
            }
        }
    }

    @Test
    fun `identity changes propagate to enabled blades`() {
        runBlocking {
            val amplitude =
                AmplitudeUnified("test-api-key", application) {
                    analytics {
                        instanceName = uniqueInstanceName("identity")
                        offline = true
                        autocapture = emptySet()
                    }
                    sessionReplay {
                        autoStart = false
                        enableRemoteConfig = false
                    }
                    experiment {
                        config =
                            ExperimentConfig.builder()
                                .fetchOnStart(false)
                                .pollOnStart(false)
                                .build()
                    }
                }

            amplitude.isBuilt.await()
            amplitude.setUserId("new-user-id")
            amplitude.setDeviceId("new-device-id")
            awaitCondition {
                amplitude.experiment?.getUser()?.userId == "new-user-id" &&
                    amplitude.experiment?.getUser()?.deviceId == "new-device-id"
            }

            assertEquals("new-device-id", amplitude.sessionReplay?.getDeviceId())
        }
    }

    @Test
    fun `session replay and experiment enablement combinations remain independent`() {
        runBlocking {
            val combinations =
                listOf(
                    BladeCombination(sessionReplay = false, experiment = false),
                    BladeCombination(sessionReplay = true, experiment = false),
                    BladeCombination(sessionReplay = false, experiment = true),
                    BladeCombination(sessionReplay = true, experiment = true),
                )

            combinations.forEach { combination ->
                val amplitude =
                    AmplitudeUnified("test-api-key", application) {
                        analytics {
                            instanceName = uniqueInstanceName("combination-$combination")
                            offline = true
                            autocapture = emptySet()
                        }
                        sessionReplay {
                            enabled = combination.sessionReplay
                            autoStart = false
                            enableRemoteConfig = false
                        }
                        experiment {
                            enabled = combination.experiment
                            config =
                                ExperimentConfig.builder()
                                    .fetchOnStart(false)
                                    .pollOnStart(false)
                                    .build()
                        }
                    }

                val events = RecordingDestinationPlugin()
                amplitude.add(events)
                amplitude.isBuilt.await()

                assertSame(amplitude, amplitude.analytics)
                assertEquals(combination.sessionReplay, amplitude.sessionReplay != null)
                assertEquals(combination.experiment, amplitude.experiment != null)
                val deviceId = "device-$combination"
                amplitude.setDeviceId(deviceId)
                amplitude.track("combination-event")
                awaitCondition { events.snapshot().any { it.eventType == "combination-event" } }
                if (combination.experiment) {
                    awaitCondition { amplitude.experiment?.getUser()?.deviceId == deviceId }
                }
                if (combination.sessionReplay) {
                    assertEquals(deviceId, amplitude.sessionReplay?.getDeviceId())
                }
            }
        }
    }

    @Test
    fun `removing one owned blade leaves the other blade available`() {
        runBlocking {
            val amplitude = createExperimentAndSessionReplayAmplitude("remove")
            amplitude.isBuilt.await()
            val sessionReplayPlugin = amplitude.plugin(SessionReplayPlugin.PLUGIN_NAME) as UniversalPlugin

            amplitude.remove(sessionReplayPlugin)

            assertNull(amplitude.sessionReplay)
            assertNotNull(amplitude.experiment)
        }
    }

    @Test
    fun `multiple wrapper instances keep identities and blade clients isolated`() {
        runBlocking {
            val first = createExperimentAndSessionReplayAmplitude("first")
            val second = createExperimentAndSessionReplayAmplitude("second")
            first.isBuilt.await()
            second.isBuilt.await()

            first.setUserId("first-user")
            first.setDeviceId("first-device")
            second.setUserId("second-user")
            second.setDeviceId("second-device")
            awaitCondition {
                first.experiment?.getUser()?.deviceId == "first-device" &&
                    second.experiment?.getUser()?.deviceId == "second-device"
            }

            first.optOut = true
            assertTrue(checkNotNull(first.sessionReplay).getOptOut())
            org.junit.Assert.assertFalse(checkNotNull(second.sessionReplay).getOptOut())
            assertNotSame(first.sessionReplay, second.sessionReplay)
            assertNotSame(first.experiment, second.experiment)
            assertEquals("first-device", first.sessionReplay?.getDeviceId())
            assertEquals("second-device", second.sessionReplay?.getDeviceId())
        }
    }

    @Test
    fun `experiment exposure is enriched by wrapper installed session replay`() {
        runBlocking {
            val flagsServer = createMockServer("[]")
            try {
                val amplitude =
                    AmplitudeUnified("test-api-key", application) {
                        analytics {
                            instanceName = uniqueInstanceName("exposure")
                            offline = true
                            autocapture = emptySet()
                        }
                        sessionReplay {
                            sampleRate = 1.0
                            autoStart = true
                            enableRemoteConfig = false
                        }
                        experiment {
                            config =
                                ExperimentConfig.builder()
                                    .initialVariants(mapOf("checkout" to Variant("treatment")))
                                    .source(Source.INITIAL_VARIANTS)
                                    .flagsServerUrl(flagsServer.url("/").toString())
                                    .automaticExposureTracking(true)
                                    .fetchOnStart(false)
                                    .pollOnStart(false)
                                    .build()
                        }
                    }
                val events = RecordingDestinationPlugin()
                amplitude.add(events)
                amplitude.isBuilt.await()
                amplitude.setDeviceId("wrapper-device")
                shadowOf(Looper.getMainLooper()).idle()
                awaitCondition { amplitude.experiment?.getUser()?.deviceId == "wrapper-device" }

                assertEquals("treatment", amplitude.experiment?.variant("checkout")?.value)
                awaitCondition { events.snapshot().any { it.eventType == "\$exposure" } }

                val exposure = events.snapshot().single { it.eventType == "\$exposure" }
                assertEquals(
                    "wrapper-device/${amplitude.sessionId}",
                    exposure.eventProperties?.get("[Amplitude] Session Replay ID"),
                )
            } finally {
                flagsServer.shutdown()
            }
        }
    }

    @Test
    fun `experiment accessor returns null when multiple experiment plugins are installed`() {
        runBlocking {
            val amplitude =
                AmplitudeUnified("test-api-key", application) {
                    analytics {
                        instanceName = uniqueInstanceName("multi-experiment")
                        offline = true
                        autocapture = emptySet()
                    }
                    sessionReplay { enabled = false }
                    experiment {
                        config =
                            ExperimentConfig.builder()
                                .fetchOnStart(false)
                                .pollOnStart(false)
                                .build()
                    }
                }

            amplitude.add(
                AmplitudeExperimentPlugin(
                    application,
                    ExperimentConfig.builder()
                        .fetchOnStart(false)
                        .pollOnStart(false)
                        .build(),
                    "second-deployment",
                ) as UniversalPlugin,
            )
            amplitude.isBuilt.await()

            assertNull(amplitude.experiment)
        }
    }

    @Test
    fun `unified library attribution is applied to events`() {
        runBlocking {
            val amplitude =
                AmplitudeUnified("test-api-key", application) {
                    analytics {
                        instanceName = uniqueInstanceName("attribution")
                        offline = true
                        autocapture = emptySet()
                    }
                    sessionReplay { enabled = false }
                    experiment { enabled = false }
                }

            amplitude.isBuilt.await()
            val event = BaseEvent().apply { eventType = "attribution" }

            amplitude.timeline.applyPlugins(Plugin.Type.Enrichment, event)
            val attributedLibrary = event.library
            amplitude.timeline.applyPlugins(Plugin.Type.Enrichment, event)

            assertTrue(event.library!!.startsWith("amplitude-android-unified/"))
            assertEquals(attributedLibrary, event.library)
        }
    }

    private fun uniqueInstanceName(name: String): String =
        "sdk-verification-unified-$name-${System.nanoTime()}"

    private fun createExperimentAndSessionReplayAmplitude(name: String): AmplitudeUnified =
        AmplitudeUnified("test-api-key", application) {
            analytics {
                instanceName = uniqueInstanceName(name)
                offline = true
                autocapture = emptySet()
            }
            sessionReplay {
                autoStart = false
                enableRemoteConfig = false
            }
            experiment {
                config =
                    ExperimentConfig.builder()
                        .fetchOnStart(false)
                        .pollOnStart(false)
                        .build()
            }
        }

    private data class BladeCombination(
        val sessionReplay: Boolean,
        val experiment: Boolean,
    )
}

private class RecordingDestinationPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Destination
    override lateinit var amplitude: com.amplitude.core.Amplitude
    private val events = Collections.synchronizedList(mutableListOf<BaseEvent>())

    override fun execute(event: BaseEvent): BaseEvent {
        events += event
        return event
    }

    fun snapshot(): List<BaseEvent> = synchronized(events) { events.toList() }
}
