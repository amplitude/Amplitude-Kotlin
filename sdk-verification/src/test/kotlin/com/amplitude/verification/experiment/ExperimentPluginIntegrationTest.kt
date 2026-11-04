@file:OptIn(GuardedAmplitudeFeature::class)

package com.amplitude.verification.experiment

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption
import com.amplitude.android.Configuration
import com.amplitude.android.GuardedAmplitudeFeature
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentClient
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.experiment.Source
import com.amplitude.experiment.Variant
import com.amplitude.experiment.experiment
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
import java.util.Collections

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExperimentPluginIntegrationTest {
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
    fun `registration timing converges on the current Analytics identity and session`() {
        runBlocking {
            val earlyAmplitude = createAmplitude("early")
            earlyAmplitude.setUserId("early-user")
            val earlyPlugin = createPlugin()
            earlyAmplitude.add(earlyPlugin as UniversalPlugin)

            val lateAmplitude = createAmplitude("late")
            lateAmplitude.setUserId("late-user")
            lateAmplitude.isBuilt.await()
            val latePlugin = createPlugin()
            lateAmplitude.add(latePlugin as UniversalPlugin)

            assertExperimentUser(earlyAmplitude, "early-user", "early-device", 42L)
            assertExperimentUser(lateAmplitude, "late-user", "late-device", 42L)
            assertSame(earlyPlugin.experimentClient, earlyAmplitude.experiment)
            assertSame(latePlugin.experimentClient, lateAmplitude.experiment)
            assertEquals(1, earlyAmplitude.plugins(AmplitudeExperimentPlugin::class.java).size)
            assertEquals(1, lateAmplitude.plugins(AmplitudeExperimentPlugin::class.java).size)

            earlyAmplitude.remove(earlyPlugin as UniversalPlugin)
            lateAmplitude.remove(latePlugin as UniversalPlugin)
        }
    }

    @Test
    fun `Analytics identity properties and session changes reach Experiment`() {
        runBlocking {
            val server = createMockServer("{}")
            try {
                val amplitude = createAmplitude("identity", minTimeBetweenSessionsMillis = 1L)
                val plugin =
                    createPlugin(
                        ExperimentConfig.builder()
                            .serverUrl(server.url("/").toString())
                            .flagsServerUrl(flagsServer.url("/").toString())
                            .fetchOnStart(false)
                            .pollOnStart(false)
                            .retryFetchOnFailure(false)
                            .automaticFetchOnAmplitudeIdentityChange(true)
                            .build(),
                    )
                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()

                amplitude.setUserId("updated-user")
                amplitude.setDeviceId("updated-device")
                amplitude.identify(mapOf("plan" to "pro"))
                amplitude.onExitForeground(1_000L)
                amplitude.onEnterForeground(2_000L)

                awaitCondition {
                    val user = plugin.experimentClient?.getUser()
                    user?.userId == "updated-user" &&
                        user.deviceId == "updated-device" &&
                        user.userProperties?.get("plan") == "pro" &&
                        user.userProperties?.get("session_id") == 2_000L
                }

                assertEquals(2_000L, amplitude.sessionId)
                amplitude.remove(plugin as UniversalPlugin)
            } finally {
                server.shutdown()
            }
        }
    }

    @Test
    fun `reset and opt in converge on the latest Analytics identity`() {
        runBlocking {
            val amplitude = createAmplitude("lifecycle", deviceId = null)
            val plugin = createPlugin()
            amplitude.add(plugin as UniversalPlugin)
            amplitude.isBuilt.await()
            awaitCondition {
                amplitude.getDeviceId() != null &&
                    plugin.experimentClient?.getUser()?.deviceId == amplitude.getDeviceId()
            }
            val originalDeviceId = amplitude.getDeviceId()

            amplitude.reset()
            awaitCondition {
                amplitude.getDeviceId() != originalDeviceId &&
                    plugin.experimentClient?.getUser()?.deviceId == amplitude.getDeviceId()
            }
            val resetDeviceId = amplitude.getDeviceId()

            amplitude.optOut = true
            amplitude.setUserId("opted-out-user")
            amplitude.setDeviceId("opted-out-device")
            amplitude.optOut = false

            awaitCondition {
                plugin.experimentClient?.getUser()?.userId == "opted-out-user" &&
                    plugin.experimentClient?.getUser()?.deviceId == "opted-out-device"
            }

            assertNotEquals(originalDeviceId, resetDeviceId)
            assertSame(plugin.experimentClient, amplitude.experiment)
            amplitude.remove(plugin as UniversalPlugin)
        }
    }

    @Test
    fun `reset clears fetched assignments without duplicating fetches`() {
        runBlocking {
            val variantsServer = MockWebServer()
            variantsServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"checkout":{"key":"treatment","value":"treatment"}}"""),
            )
            variantsServer.start()
            try {
                val amplitude = createAmplitude("assignment-reset")
                val plugin =
                    createPlugin(
                        ExperimentConfig.builder()
                            .serverUrl(variantsServer.url("/").toString())
                            .flagsServerUrl(flagsServer.url("/").toString())
                            .fetchOnStart(false)
                            .pollOnStart(false)
                            .retryFetchOnFailure(false)
                            .automaticFetchOnAmplitudeIdentityChange(false)
                            .automaticExposureTracking(false)
                            .build(),
                    )
                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()
                awaitCondition { plugin.experimentClient?.getUser()?.deviceId == "assignment-reset-device" }

                plugin.experimentClient?.fetch()?.get()
                assertEquals("treatment", plugin.experimentClient?.variant("checkout")?.value)
                assertEquals(1, variantsServer.requestCount)

                amplitude.reset()
                awaitCondition { plugin.experimentClient?.variant("checkout")?.value == null }
                amplitude.optOut = true
                amplitude.optOut = false

                assertEquals(1, variantsServer.requestCount)
                amplitude.remove(plugin as UniversalPlugin)
            } finally {
                variantsServer.shutdown()
            }
        }
    }

    @Test
    fun `automatic exposure tracking sends one Analytics exposure event`() {
        runBlocking {
            val amplitude = createAmplitude("exposure")
            val events = RecordingPlugin()
            val plugin =
                createPlugin(
                    ExperimentConfig.builder()
                        .initialVariants(mapOf("checkout" to Variant("treatment")))
                        .source(Source.INITIAL_VARIANTS)
                        .flagsServerUrl(flagsServer.url("/").toString())
                        .automaticExposureTracking(true)
                        .fetchOnStart(false)
                        .pollOnStart(false)
                        .build(),
                )
            amplitude.add(events)
            amplitude.add(plugin as UniversalPlugin)
            amplitude.isBuilt.await()
            awaitCondition { plugin.experimentClient?.getUser()?.deviceId == "exposure-device" }

            assertEquals("treatment", amplitude.experiment?.variant("checkout")?.value)
            awaitCondition { events.snapshot().count { it.eventType == "\$exposure" } == 1 }

            val exposure = events.snapshot().single { it.eventType == "\$exposure" }
            assertEquals("checkout", exposure.eventProperties?.get("flag_key"))
            assertEquals("treatment", exposure.eventProperties?.get("variant"))
            amplitude.remove(plugin as UniversalPlugin)
            amplitude.remove(events)
        }
    }

    @Test
    fun `duplicate registration keeps the first plugin and allows clean replacement`() {
        runBlocking {
            val amplitude = createAmplitude("duplicate")
            val first = createPlugin()
            val second = createPlugin()

            amplitude.add(first as UniversalPlugin)
            amplitude.add(second as UniversalPlugin)
            amplitude.isBuilt.await()
            awaitCondition { first.experimentClient != null }

            assertSame(first.experimentClient, amplitude.experiment)
            assertNull(second.experimentClient)

            amplitude.remove(first as UniversalPlugin)
            assertNull(first.experimentClient)
            amplitude.add(second as UniversalPlugin)
            awaitCondition { second.experimentClient != null }

            assertSame(second.experimentClient, amplitude.experiment)
            amplitude.remove(second as UniversalPlugin)
        }
    }

    @Test
    fun `wrapping an existing client keeps lifecycle and identity caller owned`() {
        runBlocking {
            val amplitude = createAmplitude("wrapped")
            val client = mockk<ExperimentClient>(relaxed = true)
            val plugin = AmplitudeExperimentPlugin(client)

            amplitude.add(plugin as UniversalPlugin)
            amplitude.isBuilt.await()
            amplitude.setUserId("updated-user")
            amplitude.setDeviceId("updated-device")
            amplitude.optOut = true
            amplitude.reset()
            amplitude.remove(plugin as UniversalPlugin)

            assertSame(client, plugin.experimentClient)
            verify(exactly = 0) {
                client.setUser(any())
                client.start(any())
                client.fetch(any())
                client.clear()
                client.stop()
            }
        }
    }

    private fun createAmplitude(
        name: String,
        minTimeBetweenSessionsMillis: Long = Configuration.MIN_TIME_BETWEEN_SESSIONS_MILLIS,
        deviceId: String? = "$name-device",
    ): Amplitude =
        Amplitude(
            Configuration(
                apiKey = "verification-api-key",
                context = application,
                instanceName = "sdk-verification-experiment-$name-${System.nanoTime()}",
                storageProvider = InMemoryStorageProvider(),
                identifyInterceptStorageProvider = InMemoryStorageProvider(),
                identityStorageProvider = IMIdentityStorageProvider(),
                autocapture = setOf(AutocaptureOption.SESSIONS),
                minTimeBetweenSessionsMillis = minTimeBetweenSessionsMillis,
                deviceId = deviceId,
                sessionId = 42L,
                offline = true,
                enableAutocaptureRemoteConfig = false,
                enableDiagnostics = false,
            ),
        )

    private fun createPlugin(config: ExperimentConfig = defaultExperimentConfig()): AmplitudeExperimentPlugin =
        AmplitudeExperimentPlugin(
            context = application,
            config = config,
        )

    private fun defaultExperimentConfig(): ExperimentConfig =
        ExperimentConfig.builder()
            .flagsServerUrl(flagsServer.url("/").toString())
            .fetchOnStart(false)
            .pollOnStart(false)
            .automaticExposureTracking(false)
            .build()

    private fun createMockServer(responseBody: String): MockWebServer =
        MockWebServer().apply {
            dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        MockResponse()
                            .setResponseCode(200)
                            .setBody(responseBody)
                }
            start()
        }

    private suspend fun assertExperimentUser(
        amplitude: Amplitude,
        expectedUserId: String,
        expectedDeviceId: String,
        expectedSessionId: Long,
    ) {
        amplitude.isBuilt.await()
        awaitCondition {
            val user = amplitude.experiment?.getUser()
            user?.userId == expectedUserId &&
                user.deviceId == expectedDeviceId &&
                user.userProperties?.get("session_id") == expectedSessionId
        }
        assertNotNull(amplitude.experiment)
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5_000L) {
            while (!condition()) {
                delay(10L)
            }
        }
        assertTrue(condition())
    }
}

private class RecordingPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
    private val events = Collections.synchronizedList(mutableListOf<BaseEvent>())

    override fun execute(event: BaseEvent): BaseEvent {
        events += event
        return event
    }

    fun snapshot(): List<BaseEvent> = synchronized(events) { events.toList() }
}
