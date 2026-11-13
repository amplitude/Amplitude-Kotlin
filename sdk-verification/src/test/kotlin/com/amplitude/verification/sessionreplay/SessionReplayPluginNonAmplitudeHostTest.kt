package com.amplitude.verification.sessionreplay

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.SessionReplayPlugin
import com.amplitude.android.plugins.sessionReplay
import com.amplitude.core.events.AnalyticsEvent
import com.amplitude.core.events.BaseEvent
import com.amplitude.verification.support.NonAmplitudeAnalyticsHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
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
    fun `custom AnalyticsEvent receives Replay enrichment`() {
        assumeTrue(
            "Released Replay 0.31.0 only enriches BaseEvent; enable sdkVerificationCustomEvents to reproduce",
            java.lang.Boolean.getBoolean("sdkVerificationCustomEvents"),
        )
        val host = NonAmplitudeAnalyticsHost(createEvent = { object : AnalyticsEvent by BaseEvent() {} })
        val plugin = SessionReplayPlugin(application, sampleRate = 1.0, enableRemoteConfig = false)
        try {
            host.add(plugin)
            shadowOf(Looper.getMainLooper()).idle()
            host.track("custom event", null)
            val event = host.events().single()
            assertFalse(event is BaseEvent)
            assertEquals("third-party-device/1000", event.eventProperties?.get("[Amplitude] Session Replay ID"))
        } finally {
            host.close()
        }
    }

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

        host.track("checkout", null)
        val enrichedEvent = host.events().single { it.eventType == "checkout" }
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
