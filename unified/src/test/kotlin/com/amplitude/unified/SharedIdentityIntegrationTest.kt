@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.amplitude.unified

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.AnalyticsIdentity
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.DefaultExperimentClient
import com.amplitude.experiment.ExperimentClient
import com.amplitude.experiment.ExperimentConfig
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
internal class SharedIdentityIntegrationTest {
    private val server = MockWebServer()
    private val fetches = CopyOnWriteArrayList<Future<ExperimentClient>>()
    private var checkedFetches = 0
    private lateinit var amplitude: AmplitudeUnified
    private val experiment: ExperimentClient
        get() = checkNotNull(amplitude.experiment)

    @Before
    fun setUp() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = MockResponse().setBody("{}")
            }
        server.start()

        // Experiment 1.18 always requests local flags in start(), even with fetchOnStart(false).
        // Suppress only startup; identity callbacks, setUser and fetch use the published implementation.
        mockkConstructor(DefaultExperimentClient::class)
        every { anyConstructed<DefaultExperimentClient>().start(any()) } answers {
            CompletableFuture.completedFuture(self as ExperimentClient)
        }
        every { anyConstructed<DefaultExperimentClient>().fetch(any()) } answers {
            callOriginal().also(fetches::add)
        }

        val application = ApplicationProvider.getApplicationContext<Application>()
        amplitude =
            AmplitudeUnified("api-key", application) {
                analytics {
                    instanceName = "shared-identity"
                    offline = true
                    autocapture = emptySet()
                    enableAutocaptureRemoteConfig = false
                    sessionId = 123L
                }
                sessionReplay { enabled = false }
                experiment {
                    config =
                        ExperimentConfig.builder()
                            .fetchOnStart(false)
                            .pollOnStart(false)
                            .retryFetchOnFailure(false)
                            .automaticFetchOnAmplitudeIdentityChange(true)
                            .serverUrl(server.url("/").toString())
                            .flagsServerUrl(server.url("/").toString())
                            .build()
                }
            }
        runBlocking { withTimeout(10_000) { amplitude.isBuilt.await() } }
        settle()
        assertTrue(amplitude.plugin(AmplitudeExperimentPlugin.PLUGIN_NAME) is AmplitudeExperimentPlugin)
        assertEquals(0, server.requestCount)
    }

    @After
    fun tearDown() {
        try {
            if (::amplitude.isInitialized) {
                amplitude.plugin(AmplitudeExperimentPlugin.PLUGIN_NAME)?.let(amplitude::remove)
            }
        } finally {
            unmockkConstructor(DefaultExperimentClient::class)
            server.shutdown()
        }
    }

    @Test
    fun `should replace IDs and properties in one shared identity change`() {
        amplitude.setIdentity("old-user", "old-device", mapOf("old" to true))
        settle()
        val identities = mutableListOf<AnalyticsIdentity>()
        amplitude.add(
            object : UniversalPlugin {
                override fun onIdentityChanged(identity: AnalyticsIdentity) {
                    identities.add(identity)
                }
            },
        )

        assertSame(amplitude, amplitude.setIdentity("new-user", "new-device", mapOf("plan" to "paid")))

        assertEquals(1, identities.size)
        assertEquals("new-user", identities.single().userId)
        assertEquals("new-device", identities.single().deviceId)
        assertEquals(mapOf("plan" to "paid"), identities.single().userProperties)
        assertSharedIdentity("new-user", "new-device", mapOf("plan" to "paid"))
        settle()
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `should share identify set unset and clearAll including an authoritative empty map`() {
        amplitude.identify(Identify().set("plan", "paid").set("region", "us"))
        assertSharedProperties(mapOf("plan" to "paid", "region" to "us"))
        settle()
        assertEquals(1, server.requestCount)

        amplitude.identify(Identify().unset("plan"))
        assertSharedProperties(mapOf("region" to "us"))
        settle()
        assertEquals(2, server.requestCount)

        amplitude.identify(Identify().clearAll())
        assertSharedProperties(emptyMap())
        settle()
        assertEquals(3, server.requestCount)
        assertSharedProperties(emptyMap())
    }

    @Test
    fun `should fetch once per effective change and never for unchanged identity`() {
        amplitude.setIdentity("user", "device", mapOf("plan" to "paid"))
        settle()
        assertEquals(1, server.requestCount)

        amplitude.setIdentity("user", "device", mapOf("plan" to "paid"))
        amplitude.identify(Identify().set("plan", "paid"))
        settle()
        assertEquals(1, server.requestCount)

        amplitude.identify(Identify().set("plan", "free"))
        settle()
        assertEquals(2, server.requestCount)

        // Omitting properties is an effective change to an authoritative empty map.
        amplitude.setIdentity("user", "device")
        assertSharedProperties(emptyMap())
        settle()
        assertEquals(3, server.requestCount)

        amplitude.setIdentity(amplitude.identity)
        amplitude.identify(Identify().clearAll())
        settle()
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `should clear properties and update Experiment identity on reset with one fetch`() {
        amplitude.setIdentity("user", "device", mapOf("plan" to "paid"))
        settle()

        amplitude.reset()

        assertNull(amplitude.identity.userId)
        assertNotNull(amplitude.identity.deviceId)
        assertNotEquals("device", amplitude.identity.deviceId)
        assertSharedIdentity(null, amplitude.identity.deviceId, emptyMap())
        settle()
        assertEquals(2, server.requestCount)
        assertSharedProperties(emptyMap())
    }

    private fun assertSharedIdentity(
        userId: String?,
        deviceId: String?,
        properties: Map<String, Any?>,
    ) {
        assertEquals(userId, amplitude.identity.userId)
        assertEquals(deviceId, amplitude.identity.deviceId)
        assertEquals(userId, experiment.getUser()?.userId)
        assertEquals(deviceId, experiment.getUser()?.deviceId)
        assertSharedProperties(properties)
    }

    private fun assertSharedProperties(properties: Map<String, Any?>) {
        assertEquals(properties, amplitude.identity.userProperties)
        // Experiment adds its session property to the host's authoritative map.
        assertEquals(properties + ("session_id" to amplitude.sessionId), experiment.getUser()?.userProperties)
    }

    private fun settle() {
        // A marker passes the connector's Before stage after all previously queued Identify events.
        val processed = CountDownLatch(1)
        val marker =
            object : Plugin {
                override val type = Plugin.Type.Enrichment
                override lateinit var amplitude: com.amplitude.core.Amplitude

                override fun execute(event: BaseEvent): BaseEvent? {
                    if (event.eventType != "identity-test-barrier") return event
                    processed.countDown()
                    return null
                }
            }
        amplitude.add(marker)
        amplitude.track("identity-test-barrier")
        assertTrue("Analytics timeline did not drain", processed.await(10, TimeUnit.SECONDS))
        amplitude.remove(marker)
        fetches.forEach { it.get(10, TimeUnit.SECONDS) }
        assertEquals(fetches.size, server.requestCount)
        repeat(fetches.size - checkedFetches) {
            val request = checkNotNull(server.takeRequest(10, TimeUnit.SECONDS))
            assertEquals("/sdk/v2/vardata", request.path)
            val user = JSONObject(String(Base64.getUrlDecoder().decode(request.getHeader("X-Amp-Exp-User")), Charsets.UTF_8))
            assertEquals(amplitude.identity.userId, user.optString("user_id", null))
            assertEquals(amplitude.identity.deviceId, user.optString("device_id", null))
            val properties = user.getJSONObject("user_properties")
            assertEquals(amplitude.identity.userProperties.keys + "session_id", properties.keys().asSequence().toSet())
            amplitude.identity.userProperties.forEach { (key, value) -> assertEquals(value, properties.get(key)) }
            assertEquals(amplitude.sessionId, properties.getLong("session_id"))
        }
        checkedFetches = fetches.size
    }
}
