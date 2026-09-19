package com.amplitude.verification.experiment

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.experiment.Experiment
import com.amplitude.experiment.ExperimentConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExperimentLegacyIntegrationTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `legacy Analytics integration still fetches with the host identity`() {
        runBlocking {
            val variantsServer = variantServer("legacy")
            try {
                val amplitude =
                    createVerificationAmplitude(
                        application = application,
                        name = "legacy",
                        deviceId = "legacy-device",
                    )
                amplitude.isBuilt.await()
                amplitude.setUserId("legacy-user")
                awaitCondition { amplitude.getUserId() == "legacy-user" }

                val experiment =
                    Experiment.initializeWithAmplitudeAnalytics(
                        application,
                        "legacy-deployment-key",
                        ExperimentConfig.builder()
                            .instanceName(amplitude.configuration.instanceName)
                            .serverUrl(variantsServer.url("/").toString())
                            .fetchOnStart(false)
                            .pollOnStart(false)
                            .retryFetchOnFailure(false)
                            .automaticExposureTracking(false)
                            .build(),
                    )

                experiment.fetch().get(5, TimeUnit.SECONDS)

                assertEquals("legacy", experiment.variant("checkout").value)
                val request = requireNotNull(variantsServer.takeRequest(1, TimeUnit.SECONDS))
                val encodedUser = requireNotNull(request.getHeader("X-Amp-Exp-User"))
                val user = String(Base64.getUrlDecoder().decode(encodedUser))
                assertTrue(user, user.contains("legacy-user"))
                assertTrue(user, user.contains("legacy-device"))
            } finally {
                variantsServer.shutdown()
            }
        }
    }

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
