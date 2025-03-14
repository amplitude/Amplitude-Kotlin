package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.events.Revenue
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utils.StubPlugin
import com.amplitude.core.utils.TestRunPlugin
import com.amplitude.core.utils.testAmplitude
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class AmplitudeTest {
    private lateinit var server: MockWebServer

    private lateinit var amplitude: Amplitude

    @ExperimentalCoroutinesApi
    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        server.start()

        val testApiKey = "test-123"
        val plan = Plan("test-branch", "test")
        val ingestionMetadata = IngestionMetadata("ampli", "2.0.0")
        amplitude = testAmplitude(
            Configuration(
                testApiKey,
                plan = plan,
                ingestionMetadata = ingestionMetadata,
                serverUrl = server.url("/").toString()
            )
        )
    }

    @AfterEach
    fun shutdown() {
        server.shutdown()
    }

    @Nested
    inner class TestConfiguration {
        @Test
        fun `set deviceId`() {
            val deviceId = "test-device-id"
            amplitude = testAmplitude(
                Configuration(
                    "api-key",
                    deviceId = deviceId,
                    serverUrl = server.url("/").toString()
                )
            )
            amplitude.isBuilt.invokeOnCompletion {
                assertEquals(deviceId, amplitude.store.deviceId)
                assertEquals(deviceId, amplitude.getDeviceId())
            }
        }
    }

    @Nested
    inner class TestTrack {

        @Test
        fun `test track`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val eventOptions = EventOptions()
            eventOptions.region = "CA"
            amplitude.track("test event", mapOf(Pair("foo", "bar")), eventOptions)
            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals(mapOf(Pair("foo", "bar")), it.eventProperties)
                assertEquals("CA", it.region)
                assertEquals("test", it.plan?.source)
                assertEquals("\$remote", it.ip)
                assertEquals("ampli", it.ingestionMetadata?.sourceName)
            }
        }

        @Test
        fun `test track with event object and event options`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val eventOptions = EventOptions()
            eventOptions.city = "SF"
            val event = BaseEvent()
            event.eventType = "test event"
            event.region = "CA"
            event.ip = "127.0.0.1"
            amplitude.track(event, eventOptions)
            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals("CA", it.region)
                assertEquals("SF", it.city)
                assertEquals("test", it.plan?.source)
                assertEquals("ampli", it.ingestionMetadata?.sourceName)
                assertEquals("127.0.0.1", it.ip)
            }
        }
    }

    @Nested
    inner class TestIdentify {

        @Test
        fun `test identify`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val identify = Identify()
            identify.setOnce("foo", "bar")
            identify.unset("unused")
            identify.append("boolean", true)
            amplitude.identify(identify)
            val identifyEvent = slot<IdentifyEvent>()
            verify { mockPlugin.identify(capture(identifyEvent)) }
            val expectedUserProperties = mutableMapOf<String, Any>()
            expectedUserProperties[IdentifyOperation.SET_ONCE.operationType] = mapOf(Pair("foo", "bar"))
            expectedUserProperties[IdentifyOperation.UNSET.operationType] = mapOf(Pair("unused", "-"))
            expectedUserProperties[IdentifyOperation.APPEND.operationType] =
                mapOf(Pair("boolean", true))
            identifyEvent.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals(expectedUserProperties, it.userProperties)
            }
        }

        @Test
        fun `test identify with userProperties`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val userProperties = mutableMapOf<String, Any>()
            userProperties["foo"] = "bar"
            userProperties["boolean"] = true
            amplitude.identify(userProperties)
            val identifyEvent = slot<IdentifyEvent>()
            verify { mockPlugin.identify(capture(identifyEvent)) }
            val expectedUserProperties = mutableMapOf<String, Any>()
            expectedUserProperties[IdentifyOperation.SET.operationType] = mapOf(Pair("foo", "bar"), Pair("boolean", true))
            identifyEvent.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals(expectedUserProperties, it.userProperties)
            }
        }
    }

    @Nested
    inner class TestSetGroup {
        @Test
        fun `test setGroup with one group`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val eventOptions = EventOptions()
            eventOptions.region = "CA"
            amplitude.setGroup("test", "groupName", eventOptions)
            val identifyEvent = slot<IdentifyEvent>()
            verify { mockPlugin.identify(capture(identifyEvent)) }
            val expectedGroups = mutableMapOf("test" to "groupName")
            identifyEvent.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals("CA", it.region)
                assertEquals(expectedGroups, it.groups)
            }
        }

        @Test
        fun `test setGroup with list of groups`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val eventOptions = EventOptions()
            eventOptions.region = "CA"
            val groups = arrayOf("group1", "group2")
            amplitude.setGroup("test", groups, eventOptions)
            val identifyEvent = slot<IdentifyEvent>()
            verify { mockPlugin.identify(capture(identifyEvent)) }
            val expectedGroups = mutableMapOf("test" to groups)
            identifyEvent.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals("CA", it.region)
                assertEquals(expectedGroups, it.groups)
            }
        }
    }

    @Nested
    inner class TestGroupIdentify {

        @Test
        fun `test groupIdentify`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val eventOptions = EventOptions()
            eventOptions.region = "CA"
            val identify = Identify().set("foo", "bar")
            amplitude.groupIdentify("test event", "group name", identify, eventOptions)
            val groupIdentify = slot<GroupIdentifyEvent>()
            verify { mockPlugin.groupIdentify(capture(groupIdentify)) }
            groupIdentify.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals("CA", it.region)
                assertEquals(mapOf(Pair(IdentifyOperation.SET.operationType, mapOf(Pair("foo", "bar")))), it.groupProperties)
            }
        }

        @Test
        fun `test groupIdentify with groupProperties`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val groupProperties = mutableMapOf<String, Any>()
            groupProperties["foo"] = "bar"
            groupProperties["boolean"] = true
            amplitude.groupIdentify("test event", "group name", groupProperties)
            val groupIdentify = slot<GroupIdentifyEvent>()
            verify { mockPlugin.groupIdentify(capture(groupIdentify)) }
            groupIdentify.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals(mapOf(Pair(IdentifyOperation.SET.operationType, mapOf(Pair("foo", "bar"), Pair("boolean", true)))), it.groupProperties)
            }
        }
    }

    @Nested
    inner class TestRevenue {

        @Test
        fun `test revenue`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val eventOptions = EventOptions()
            eventOptions.region = "CA"
            val revenue = Revenue()
            revenue.price = 9.99
            revenue.quantity = 25
            amplitude.revenue(revenue, eventOptions)
            val revenueEvent = slot<RevenueEvent>()
            verify { mockPlugin.revenue(capture(revenueEvent)) }
            val expectedProperties = mutableMapOf<String, Any>()
            expectedProperties[Revenue.REVENUE_PRICE] = 9.99
            expectedProperties[Revenue.REVENUE_QUANTITY] = 25
            revenueEvent.captured.let {
                assertEquals("user_id", it.userId)
                assertEquals("device_id", it.deviceId)
                assertEquals("${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}", it.library)
                assertEquals("CA", it.region)
                assertEquals(expectedProperties, it.eventProperties)
            }
        }
    }

    @Nested
    inner class TestPlugins {
        @Test
        fun `Can add plugins to amplitude`() {
            val middleware = object : Plugin {
                override val type = Plugin.Type.Enrichment
                override lateinit var amplitude: Amplitude
            }
            amplitude.add(middleware)
            amplitude.timeline.plugins[Plugin.Type.Enrichment]?.size()?.let {
                assertEquals(
                    2,
                    it
                )
            } ?: fail()
        }

        @Test
        fun `Can remove plugins from amplitude`() {
            val middleware = object : Plugin {
                override val type = Plugin.Type.Enrichment
                override lateinit var amplitude: Amplitude
            }
            amplitude.add(middleware)
            amplitude.remove(middleware)
            amplitude.timeline.plugins[Plugin.Type.Enrichment]?.size()?.let {
                assertEquals(
                    1, // SegmentLog is the other added at startup
                    it
                )
            } ?: fail()
        }

        @Test
        fun `event runs through chain of plugins`() {
            val testPlugin1 = TestRunPlugin {}
            val testPlugin2 = TestRunPlugin {}
            amplitude
                .add(testPlugin1)
                .add(testPlugin2)
            amplitude.track("test event")
            assertTrue(testPlugin1.ran)
            assertTrue(testPlugin2.ran)
        }
    }

    @Nested
    inner class TestFlush {

        @Test
        fun `test flush`() {
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            val event = BaseEvent()
            event.eventType = "test event"
            var callbackCalled = false
            amplitude.track(event) { e, status, message ->
                assertEquals("test event", e.eventType)
                assertEquals(200, status)
                assertEquals("Event sent success.", message)
                callbackCalled = true
            }
            amplitude.flush()
            assertTrue(callbackCalled)
        }
    }

    @Nested
    inner class TestReset {
        @Test
        fun `test reset`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)

            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")
            amplitude.reset()
            amplitude.track("test event")

            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }

            track.captured.let {
                assertEquals(null, it.userId)
                assertNotEquals("device_id", it.deviceId)
                assertEquals("test event", it.eventType)
            }
        }
    }
}
