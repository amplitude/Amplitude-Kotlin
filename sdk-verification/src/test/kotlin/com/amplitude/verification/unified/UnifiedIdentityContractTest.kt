package com.amplitude.verification.unified

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.unified.AmplitudeUnified
import com.amplitude.verification.experiment.awaitCondition
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UnifiedIdentityContractTest {
    private val server = MockWebServer()
    private val clients = mutableListOf<AmplitudeUnified>()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setBody(if (request.path == "/sdk/v2/flags") "[]" else "{}")
        }
        server.start()
    }

    @After
    fun tearDown() {
        clients.forEach { client ->
            client.plugins(UniversalPlugin::class.java).forEach { client.remove(it) }
        }
        server.shutdown()
    }

    @Test
    fun `identity then track uses the new identity and authoritative properties across blades`() = runBlocking {
        val amplitude = createAmplitude()
        val events = RecordingEvents()
        amplitude.add(events)
        amplitude.setIdentity("user", "device", mapOf("plan" to "paid", "region" to "us"))
        amplitude.track("after identity")

        assertEquals("user", amplitude.identity.userId)
        assertEquals("device", amplitude.sessionReplay?.getDeviceId())
        awaitCondition { events.events.any { it.eventType == "after identity" } }
        val event = events.events.single { it.eventType == "after identity" }
        assertEquals("user", event.userId)
        assertEquals("device", event.deviceId)
        assertExperimentIdentity(amplitude, mapOf("plan" to "paid", "region" to "us"))

        amplitude.identify(Identify().unset("region").set("plan", "free"))
        assertExperimentIdentity(amplitude, mapOf("plan" to "free"))
        amplitude.identify(Identify().clearAll())
        assertExperimentIdentity(amplitude, emptyMap())

        amplitude.setIdentity("user", "device", mapOf("old" to true))
        amplitude.reset()
        assertNull(amplitude.identity.userId)
        assertNotEquals("device", amplitude.identity.deviceId)
        assertEquals(amplitude.identity.deviceId, amplitude.sessionReplay?.getDeviceId())
        assertExperimentIdentity(amplitude, emptyMap())
    }

    @Test
    fun `removed Experiment stops receiving identity changes while Replay and Analytics continue`() = runBlocking {
        val amplitude = createAmplitude()
        amplitude.setIdentity("original", "original-device")
        val experiment = checkNotNull(amplitude.experiment)
        awaitCondition { experiment.getUser()?.userId == "original" }
        amplitude.remove(checkNotNull(amplitude.plugin(AmplitudeExperimentPlugin.PLUGIN_NAME)) as UniversalPlugin)
        val events = RecordingEvents()
        amplitude.add(events)

        amplitude.setIdentity("replacement", "replacement-device")
        amplitude.track("after removal")
        awaitCondition { events.events.any { it.eventType == "after removal" } }

        assertNull(amplitude.experiment)
        assertEquals("original", experiment.getUser()?.userId)
        assertEquals("replacement-device", amplitude.sessionReplay?.getDeviceId())
        assertEquals("replacement", events.events.single { it.eventType == "after removal" }.userId)
    }

    @Test
    fun `Replay setup failure leaves Experiment and Analytics usable`() = runBlocking {
        mockkConstructor(SessionReplayPlugin::class)
        every { anyConstructed<SessionReplayPlugin>().setup(any<com.amplitude.core.Amplitude>()) } throws
            IllegalStateException("verification setup failure")
        try {
            val amplitude = createAmplitude()
            val events = RecordingEvents()
            amplitude.add(events)
            amplitude.track("after setup failure")
            awaitCondition { events.events.any { it.eventType == "after setup failure" } }

            assertNull(amplitude.sessionReplay)
            assertNotNull(amplitude.experiment)
            assertExperimentIdentity(amplitude, emptyMap())
        } finally {
            unmockkConstructor(SessionReplayPlugin::class)
        }
    }

    private suspend fun createAmplitude(): AmplitudeUnified {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val amplitude = AmplitudeUnified("verification-api-key", application) {
            analytics {
                instanceName = "verification-identity-${System.nanoTime()}"
                offline = true
                autocapture = emptySet()
                enableAutocaptureRemoteConfig = false
                enableDiagnostics = false
            }
            sessionReplay {
                autoStart = false
                enableRemoteConfig = false
            }
            experiment {
                config = ExperimentConfig.builder()
                    .serverUrl(server.url("/").toString())
                    .flagsServerUrl(server.url("/").toString())
                    .fetchOnStart(false)
                    .pollOnStart(false)
                    .automaticFetchOnAmplitudeIdentityChange(false)
                    .retryFetchOnFailure(false)
                    .build()
            }
        }
        clients += amplitude
        withTimeout(10_000) { amplitude.isBuilt.await() }
        return amplitude
    }

    private suspend fun assertExperimentIdentity(amplitude: AmplitudeUnified, properties: Map<String, Any?>) {
        val experiment = checkNotNull(amplitude.experiment)
        awaitCondition {
            experiment.getUser()?.userId == amplitude.identity.userId &&
                experiment.getUser()?.deviceId == amplitude.identity.deviceId &&
                experiment.getUser()?.userProperties == properties + ("session_id" to amplitude.sessionId)
        }
        assertEquals(properties, amplitude.identity.userProperties)
        experiment.fetch().get(5, TimeUnit.SECONDS)
        var request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        while (request.path != "/sdk/v2/vardata") {
            request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        }
        val user = JSONObject(String(Base64.getUrlDecoder().decode(request.getHeader("X-Amp-Exp-User")), Charsets.UTF_8))
        assertEquals(amplitude.identity.userId, user.optString("user_id", null))
        assertEquals(amplitude.identity.deviceId, user.optString("device_id", null))
        val actualProperties = user.getJSONObject("user_properties")
        assertEquals(properties.keys + "session_id", actualProperties.keys().asSequence().toSet())
        properties.forEach { (key, value) -> assertEquals(value, actualProperties.get(key)) }
    }

    private class RecordingEvents : Plugin {
        override val type = Plugin.Type.Destination
        override lateinit var amplitude: com.amplitude.core.Amplitude
        val events = CopyOnWriteArrayList<BaseEvent>()

        override fun execute(event: BaseEvent): BaseEvent {
            events += event
            return event
        }
    }
}
