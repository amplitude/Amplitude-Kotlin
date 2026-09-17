package com.amplitude.verification.engagement

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.engagement.AmplitudeEngagementPluginFactory
import com.amplitude.android.engagement.engagement
import com.amplitude.core.events.BaseEvent
import com.amplitude.verification.support.NonAmplitudeAnalyticsHost
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EngagementPluginNonAmplitudeHostTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.respondToEngagementRequests()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `third-party host supplies lifecycle and event pipeline`() {
        val host =
            NonAmplitudeAnalyticsHost(
                initialUserProperties = mapOf("plan" to "free"),
                initialSessionId = 1_000L,
            )
        val plugin = AmplitudeEngagementPluginFactory.make(application, server.engagementOptions())

        host.add(plugin)
        val engagement = awaitEngagement(host)

        host.updateIdentity(
            userId = "updated-user",
            deviceId = "updated-device",
            userProperties = mapOf("plan" to "pro"),
        )
        host.updateSessionId(2_000L)
        host.updateOptOut(true)
        host.updateOptOut(false)
        host.reset("reset-device")

        val event = BaseEvent().apply { eventType = "checkout" }
        assertSame(event, host.process(event))
        assertSame(engagement, awaitEngagement(host))

        host.remove(plugin)
        awaitNoEngagement(host)
        assertNull(host.engagement)
    }
}
