package com.amplitude.verification.sessionreplay

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.android.plugins.sessionReplay
import com.amplitude.core.events.BaseEvent
import com.amplitude.verification.support.NonAmplitudeAnalyticsHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SessionReplayPluginNonAmplitudeHostTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `third-party host supplies identity session opt out and event pipeline`() {
        val host = NonAmplitudeAnalyticsHost(initialSessionId = 1_000L)
        val plugin =
            SessionReplayPlugin(
                context = application,
                sampleRate = 1.0,
                enableRemoteConfig = false,
                autoStart = true,
            )

        host.add(plugin)
        shadowOf(Looper.getMainLooper()).idle()

        assertSame(plugin.sessionReplayClient, host.sessionReplay)
        assertEquals("third-party-device", plugin.getDeviceId())
        assertEquals(1_000L, plugin.getSessionId())
        assertTrue(plugin.isRecording())

        val event =
            BaseEvent().apply {
                eventType = "checkout"
                deviceId = host.identity.deviceId
                sessionId = host.sessionId
            }
        val enrichedEvent = host.process(event)
        assertEquals(
            "third-party-device/1000",
            enrichedEvent?.eventProperties?.get("[Amplitude] Session Replay ID"),
        )

        host.updateIdentity(deviceId = "updated-device")
        host.updateSessionId(2_000L)
        host.updateOptOut(true)
        assertEquals("updated-device", plugin.getDeviceId())
        assertEquals(2_000L, plugin.getSessionId())
        assertTrue(plugin.getOptOut())

        host.updateOptOut(false)
        assertFalse(plugin.getOptOut())

        host.remove(plugin)
        assertNull(host.sessionReplay)
    }
}
