@file:OptIn(com.amplitude.android.GuardedAmplitudeFeature::class)

package com.amplitude.verification.sessionreplay

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.android.plugins.sessionReplay
import com.amplitude.android.sessionreplay.SessionReplay
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.platform.plugins
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionReplayPluginIntegrationTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `owned plugin registers and exposes a Session Replay client`() {
        runBlocking {
            val amplitude = createSessionReplayVerificationAmplitude(application, "registration")
            val plugin = createPlugin()

            amplitude.add(plugin as UniversalPlugin)
            amplitude.isBuilt.await()

            assertNotNull(plugin.sessionReplayClient)
            assertSame(plugin.sessionReplayClient, amplitude.sessionReplay)
            assertEquals(amplitude.getDeviceId(), plugin.getDeviceId())
            assertEquals(amplitude.sessionId, plugin.getSessionId())

            amplitude.remove(plugin as UniversalPlugin)
            assertNull(plugin.sessionReplayClient)
            assertNull(amplitude.sessionReplay)
        }
    }

    @Test
    fun `duplicate registration keeps one Session Replay plugin`() {
        runBlocking {
            val amplitude = createSessionReplayVerificationAmplitude(application, "duplicate")
            val first = createPlugin()
            val duplicate = createPlugin()

            amplitude.add(first as UniversalPlugin)
            amplitude.add(duplicate as UniversalPlugin)
            amplitude.isBuilt.await()

            assertEquals(1, amplitude.plugins(SessionReplayPlugin::class.java).size)
            assertSame(first.sessionReplayClient, amplitude.sessionReplay)
            assertNull(duplicate.sessionReplayClient)

            amplitude.remove(first as UniversalPlugin)
        }
    }

    @Test
    fun `host identity changes and reset propagate to Session Replay`() {
        runBlocking {
            val amplitude = createSessionReplayVerificationAmplitude(application, "identity")
            val plugin = createPlugin()
            amplitude.add(plugin as UniversalPlugin)
            amplitude.isBuilt.await()

            amplitude.setDeviceId("updated-device")
            assertEquals("updated-device", plugin.getDeviceId())

            amplitude.reset()
            assertNotEquals("updated-device", amplitude.getDeviceId())
            assertEquals(amplitude.getDeviceId(), plugin.getDeviceId())

            amplitude.remove(plugin as UniversalPlugin)
        }
    }

    @Test
    fun `host opt out propagates in both directions`() {
        runBlocking {
            val amplitude = createSessionReplayVerificationAmplitude(application, "opt-out")
            val plugin = createPlugin()
            amplitude.add(plugin as UniversalPlugin)
            amplitude.isBuilt.await()

            amplitude.optOut = true
            assertTrue(plugin.getOptOut())

            amplitude.optOut = false
            assertFalse(plugin.getOptOut())

            amplitude.remove(plugin as UniversalPlugin)
        }
    }

    @Test
    fun `custom session id overrides the host until cleared`() {
        runBlocking {
            val amplitude = createSessionReplayVerificationAmplitude(application, "custom-session")
            val plugin = createPlugin()
            plugin.setCustomSessionId("checkout-flow")

            amplitude.add(plugin as UniversalPlugin)
            amplitude.isBuilt.await()

            assertEquals("checkout-flow", plugin.getCustomSessionId())

            plugin.setCustomSessionId(null)
            assertNull(plugin.getCustomSessionId())
            assertEquals(amplitude.sessionId, plugin.getSessionId())

            amplitude.remove(plugin as UniversalPlugin)
        }
    }

    @Test
    fun `recording client enriches a matching Analytics event`() {
        runBlocking {
            val amplitude = createSessionReplayVerificationAmplitude(application, "enrichment")
            val plugin =
                SessionReplayPlugin(
                    context = application,
                    sampleRate = 1.0,
                    enableRemoteConfig = false,
                    autoStart = true,
                )
            amplitude.add(plugin as UniversalPlugin)
            amplitude.isBuilt.await()
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(plugin.isRecording())
            val event =
                BaseEvent().apply {
                    deviceId = amplitude.getDeviceId()
                    sessionId = amplitude.sessionId
                }

            val enrichedEvent = plugin.execute(event)

            assertEquals(
                "${amplitude.getDeviceId()}/${amplitude.sessionId}",
                enrichedEvent.eventProperties?.get("[Amplitude] Session Replay ID"),
            )

            amplitude.remove(plugin as UniversalPlugin)
        }
    }

    @Test
    fun `wrapped client remains caller owned`() {
        runBlocking {
            val amplitude = createSessionReplayVerificationAmplitude(application, "wrapped")
            val external =
                SessionReplay(
                    apiKey = "wrapped-api-key",
                    context = application,
                    deviceId = "external-device",
                    sessionId = 101L,
                    sampleRate = 0.0,
                    enableRemoteConfig = false,
                    autoStart = false,
                )
            val plugin = SessionReplayPlugin(external)

            try {
                amplitude.add(plugin as UniversalPlugin)
                amplitude.isBuilt.await()
                amplitude.setDeviceId("host-device")
                amplitude.optOut = true

                assertSame(external, amplitude.sessionReplay)
                assertEquals("external-device", external.getDeviceId())
                assertEquals(101L, external.getSessionId())
                assertFalse(external.getOptOut())

                amplitude.remove(plugin as UniversalPlugin)
                assertSame(external, plugin.sessionReplayClient)
            } finally {
                external.shutdown()
            }
        }
    }

    private fun createPlugin(): SessionReplayPlugin =
        SessionReplayPlugin(
            context = application,
            sampleRate = 0.0,
            enableRemoteConfig = false,
            autoStart = false,
        )
}
