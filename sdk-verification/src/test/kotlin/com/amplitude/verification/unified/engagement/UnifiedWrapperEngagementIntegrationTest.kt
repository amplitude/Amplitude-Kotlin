package com.amplitude.verification.unified.engagement

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.unified.AmplitudeUnified
import com.amplitude.verification.engagement.EngagementRequestRecorder
import com.amplitude.verification.engagement.awaitEngagement
import com.amplitude.verification.engagement.engagementOptions
import com.amplitude.verification.engagement.respondToEngagementRequests
import com.amplitude.verification.experiment.awaitCondition
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UnifiedWrapperEngagementIntegrationTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private lateinit var requests: EngagementRequestRecorder

    @Before
    fun setUp() {
        server = MockWebServer()
        requests = server.respondToEngagementRequests()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `one wrapper keeps identity consistent across all blades`() {
        runBlocking {
            val amplitude = createAmplitude(name = "identity", BladeCombination())
            amplitude.isBuilt.await()

            amplitude.setUserId("wrapper-user")
            amplitude.setDeviceId("wrapper-device")
            awaitCondition {
                amplitude.experiment?.getUser()?.userId == "wrapper-user" &&
                    amplitude.experiment?.getUser()?.deviceId == "wrapper-device"
            }
            val engagement = awaitEngagement(amplitude)
            engagement.refresh()
            val engagementUser =
                requests.awaitDecideUser { user ->
                    user.optString("user_id") == "wrapper-user" &&
                        user.optString("device_id") == "wrapper-device"
                }

            assertEquals("wrapper-device", amplitude.sessionReplay?.getDeviceId())
            assertEquals("wrapper-user", engagementUser.value.getString("user_id"))
            assertEquals("wrapper-device", engagementUser.value.getString("device_id"))
        }
    }

    @Test
    fun `all blades and each single disabled combination remain independent`() {
        runBlocking {
            val combinations =
                listOf(
                    BladeCombination(),
                    BladeCombination(sessionReplay = false),
                    BladeCombination(experiment = false),
                    BladeCombination(engagement = false),
                )

            combinations.forEachIndexed { index, combination ->
                val amplitude = createAmplitude(name = "combination-$index", combination)
                amplitude.isBuilt.await()

                assertEquals(combination.sessionReplay, amplitude.sessionReplay != null)
                assertEquals(combination.experiment, amplitude.experiment != null)
                if (combination.engagement) {
                    assertNotNull(awaitEngagement(amplitude))
                } else {
                    assertNull(amplitude.engagement)
                }
            }
        }
    }

    private fun createAmplitude(
        name: String,
        combination: BladeCombination,
    ): AmplitudeUnified =
        AmplitudeUnified("test-api-key", application) {
            analytics {
                instanceName = "sdk-verification-unified-$name-${System.nanoTime()}"
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
            engagement {
                enabled = combination.engagement
                options = server.engagementOptions()
            }
        }

    private data class BladeCombination(
        val sessionReplay: Boolean = true,
        val experiment: Boolean = true,
        val engagement: Boolean = true,
    )
}
