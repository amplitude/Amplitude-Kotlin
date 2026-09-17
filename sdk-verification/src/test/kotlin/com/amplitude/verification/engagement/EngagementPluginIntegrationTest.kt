@file:OptIn(com.amplitude.android.GuardedAmplitudeFeature::class)

package com.amplitude.verification.engagement

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.engagement.AmplitudeEngagementPluginFactory
import com.amplitude.android.engagement.engagement
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.platform.plugins
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EngagementPluginIntegrationTest {
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
    fun `plugin registers before Analytics startup`() {
        runBlocking {
            val amplitude = createEngagementVerificationAmplitude(name = "before-startup")
            val plugin = createPlugin()

            amplitude.add(plugin)
            amplitude.isBuilt.await()

            awaitEngagement(amplitude)
            amplitude.remove(plugin)
        }
    }

    @Test
    fun `plugin registers after Analytics startup`() {
        runBlocking {
            val amplitude = createEngagementVerificationAmplitude(name = "after-startup")
            amplitude.isBuilt.await()
            val plugin = createPlugin()

            amplitude.add(plugin)

            awaitEngagement(amplitude)
            amplitude.remove(plugin)
        }
    }

    @Test
    fun `host identity identify and reset keep Engagement usable`() {
        runBlocking {
            val amplitude = createEngagementVerificationAmplitude(name = "identity")
            val plugin = createPlugin()
            amplitude.add(plugin)
            amplitude.isBuilt.await()
            val engagement = awaitEngagement(amplitude)
            val initial =
                requests.awaitDecideUser { user ->
                    user.optString("user_id") == "identity-user" &&
                        user.optString("device_id") == "identity-device"
                }

            amplitude.setUserId("updated-user")
            amplitude.setDeviceId("updated-device")
            amplitude.identify(Identify().set("plan", "pro"))
            engagement.refresh()
            val identified =
                requests.awaitDecideUser(afterIndex = initial.index + 1) { user ->
                    user.optString("user_id") == "updated-user" &&
                        user.optString("device_id") == "updated-device" &&
                        user.getJSONObject("user_properties").optString("plan") == "pro"
                }

            amplitude.identify(Identify().unset("plan"))
            engagement.refresh()
            val unset =
                requests.awaitDecideUser(afterIndex = identified.index + 1) { user ->
                    user.optString("user_id") == "updated-user" &&
                        !user.getJSONObject("user_properties").has("plan")
                }

            amplitude.reset()
            engagement.refresh()
            val reset =
                requests.awaitDecideUser(afterIndex = unset.index + 1) { user ->
                    !user.has("user_id") && user.getJSONObject("user_properties").length() == 0
                }

            assertFalse(reset.value.has("user_id"))
            assertEquals(0, reset.value.getJSONObject("user_properties").length())
            assertSame(engagement, awaitEngagement(amplitude))
            amplitude.remove(plugin)
        }
    }

    @Test
    fun `starting opted out suppresses Engagement requests`() {
        runBlocking {
            val amplitude = createEngagementVerificationAmplitude(name = "opt-out", optOut = true)
            val plugin = createPlugin()
            amplitude.add(plugin)
            amplitude.isBuilt.await()
            val engagement = awaitEngagement(amplitude)

            requests.awaitNoDecide()
            assertSame(engagement, awaitEngagement(amplitude))
            amplitude.remove(plugin)
        }
    }

    @Test
    fun `duplicate registration keeps the first plugin`() {
        runBlocking {
            val amplitude = createEngagementVerificationAmplitude(name = "duplicate")
            val first = createPlugin()
            val duplicate = createPlugin()
            amplitude.add(first)
            amplitude.isBuilt.await()
            val firstEngagement = awaitEngagement(amplitude)

            amplitude.add(duplicate)

            assertEquals(
                1,
                amplitude.plugins(UniversalPlugin::class.java)
                    .count { it.name == AmplitudeEngagementPluginFactory.NAME },
            )
            assertSame(firstEngagement, amplitude.engagement)
            amplitude.remove(first)
        }
    }

    @Test
    fun `plugin can be removed and registered again`() {
        runBlocking {
            val amplitude = createEngagementVerificationAmplitude(name = "replacement")
            val plugin = createPlugin()
            amplitude.add(plugin)
            amplitude.isBuilt.await()
            val firstEngagement = awaitEngagement(amplitude)

            amplitude.remove(plugin)
            awaitNoEngagement(amplitude)
            amplitude.add(plugin)

            assertNotSame(firstEngagement, awaitEngagement(amplitude))
            amplitude.remove(plugin)
        }
    }

    @Test
    fun `host accessor operations trigger a refresh`() {
        runBlocking {
            val amplitude = createEngagementVerificationAmplitude(name = "operations")
            val plugin = createPlugin()
            amplitude.add(plugin)
            amplitude.isBuilt.await()
            val engagement = awaitEngagement(amplitude)
            val initial = requests.awaitDecideUser()

            engagement.disable()
            engagement.enable()
            engagement.screen("Checkout")
            engagement.updateLanguage("fr")
            engagement.refresh()
            engagement.closeAll()

            requests.awaitDecideUser(afterIndex = initial.index + 1)
            amplitude.remove(plugin)
        }
    }

    private fun createPlugin(): UniversalPlugin =
        AmplitudeEngagementPluginFactory.make(application, server.engagementOptions())
}
