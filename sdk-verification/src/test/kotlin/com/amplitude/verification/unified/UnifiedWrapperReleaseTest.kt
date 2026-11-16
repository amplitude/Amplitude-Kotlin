package com.amplitude.verification.unified

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.core.platform.plugins
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.unified.AmplitudeUnified
import com.amplitude.verification.experiment.awaitCondition
import com.amplitude.verification.experiment.createMockServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Release-readiness checks for the paths customers hit first when adopting the wrapper. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UnifiedWrapperReleaseTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `uploaded events carry the unified library attribution`() {
        runBlocking {
            val server = createMockServer("""{"code":200}""")
            try {
                val amplitude =
                    AmplitudeUnified("test-api-key", application) {
                        analytics {
                            instanceName = uniqueInstanceName("upload")
                            serverUrl = server.url("/2/httpapi").toString()
                            flushQueueSize = 1
                            autocapture = emptySet()
                        }
                        sessionReplay { enabled = false }
                        experiment { enabled = false }
                    }
                amplitude.isBuilt.await()

                amplitude.track("uploaded")

                val request = checkNotNull(server.takeRequest(10, TimeUnit.SECONDS)) { "no upload within 10s" }
                val event = JSONObject(request.body.readUtf8()).getJSONArray("events").getJSONObject(0)
                assertEquals("uploaded", event.getString("event_type"))
                val library = event.getString("library")
                assertTrue(library, library.startsWith("amplitude-android-unified/"))
                assertTrue(library, library.contains("-amplitude-analytics-android/"))
            } finally {
                server.shutdown()
            }
        }
    }

    @Test
    fun `experiment uses the analytics api key when no deployment key is set`() {
        runBlocking {
            val server = createMockServer("{}")
            try {
                val amplitude =
                    AmplitudeUnified("analytics-api-key", application) {
                        analytics {
                            instanceName = uniqueInstanceName("default-deployment-key")
                            offline = true
                            autocapture = emptySet()
                        }
                        sessionReplay { enabled = false }
                        experiment { config = localExperimentConfig(server.url("/").toString()) }
                    }
                amplitude.isBuilt.await()

                amplitude.experiment?.fetch()?.get(5, TimeUnit.SECONDS)

                val request = server.takeRequest(5, TimeUnit.SECONDS)
                assertEquals("Api-Key analytics-api-key", request?.getHeader("Authorization"))
            } finally {
                server.shutdown()
            }
        }
    }

    @Test
    fun `replacing a wrapper with the same instance name retires its blades`() {
        runBlocking {
            val instanceName = uniqueInstanceName("replacement")
            val first = bothBladesAmplitude(instanceName)
            first.isBuilt.await()
            assertNotNull(first.sessionReplay)
            assertNotNull(first.experiment)

            val second = bothBladesAmplitude(instanceName)
            second.isBuilt.await()

            awaitCondition { first.sessionReplay == null && first.experiment == null }
            assertNotNull(second.sessionReplay)
            assertNotNull(second.experiment)

            second.setDeviceId("replacement-device")
            assertEquals("replacement-device", second.sessionReplay?.getDeviceId())
            awaitCondition { second.experiment?.getUser()?.deviceId == "replacement-device" }
        }
    }

    @Test
    fun `a manually added session replay plugin does not replace the wrapper blade`() {
        runBlocking {
            val amplitude = bothBladesAmplitude(uniqueInstanceName("manual-session-replay"))
            amplitude.isBuilt.await()
            val wrapperClient = checkNotNull(amplitude.sessionReplay)

            amplitude.add(SessionReplayPlugin(application, sampleRate = 1.0, enableRemoteConfig = false, autoStart = false))

            assertEquals(1, amplitude.plugins<SessionReplayPlugin>().size)
            assertSame(wrapperClient, amplitude.sessionReplay)
        }
    }

    @Test
    fun `starting opted out keeps blades idle until the user opts in`() {
        runBlocking {
            val server = createMockServer("{}")
            try {
                val amplitude =
                    AmplitudeUnified("test-api-key", application) {
                        analytics {
                            instanceName = uniqueInstanceName("opted-out")
                            offline = true
                            autocapture = emptySet()
                            optOut = true
                            deviceId = "opted-out-device"
                            sessionId = 42L
                        }
                        sessionReplay {
                            sampleRate = 1.0
                            enableRemoteConfig = false
                        }
                        experiment {
                            config =
                                ExperimentConfig.builder()
                                    .serverUrl(server.url("/").toString())
                                    .flagsServerUrl(server.url("/").toString())
                                    .fetchOnStart(true)
                                    .pollOnStart(false)
                                    .retryFetchOnFailure(false)
                                    .build()
                        }
                    }
                amplitude.isBuilt.await()

                assertTrue(checkNotNull(amplitude.sessionReplay).getOptOut())
                assertFalse(checkNotNull(amplitude.sessionReplay).shouldRecord())
                assertNull("Experiment fetched while opted out", server.takeRequest(500, TimeUnit.MILLISECONDS))

                amplitude.optOut = false

                assertFalse(checkNotNull(amplitude.sessionReplay).getOptOut())
                assertNotNull("Experiment did not start after opt in", server.takeRequest(5, TimeUnit.SECONDS))
            } finally {
                server.shutdown()
            }
        }
    }

    private fun bothBladesAmplitude(instanceName: String): AmplitudeUnified =
        AmplitudeUnified("test-api-key", application) {
            analytics {
                this.instanceName = instanceName
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

    private fun localExperimentConfig(serverUrl: String): ExperimentConfig =
        ExperimentConfig.builder()
            .serverUrl(serverUrl)
            .flagsServerUrl(serverUrl)
            .fetchOnStart(false)
            .pollOnStart(false)
            .retryFetchOnFailure(false)
            .build()

    private fun uniqueInstanceName(name: String): String = "sdk-verification-unified-release-$name-${System.nanoTime()}"
}
