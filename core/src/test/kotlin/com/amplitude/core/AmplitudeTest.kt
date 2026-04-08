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
import com.amplitude.core.platform.DestinationPlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.JSONUtil
import com.amplitude.core.utils.FakeAmplitude
import com.amplitude.core.utils.StubPlugin
import com.amplitude.core.utils.TestRunPlugin
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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
        amplitude =
            FakeAmplitude(
                Configuration(
                    testApiKey,
                    plan = plan,
                    ingestionMetadata = ingestionMetadata,
                    serverUrl = server.url("/").toString(),
                ),
                testDispatcher = UnconfinedTestDispatcher(),
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
            amplitude =
                FakeAmplitude(
                    Configuration(
                        "api-key",
                        deviceId = deviceId,
                        serverUrl = server.url("/").toString(),
                    ),
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
                assertEquals(
                    mapOf(Pair(IdentifyOperation.SET.operationType, mapOf(Pair("foo", "bar"), Pair("boolean", true)))),
                    it.groupProperties,
                )
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
            val middleware =
                object : Plugin {
                    override val type = Plugin.Type.Enrichment
                    override lateinit var amplitude: Amplitude
                }
            amplitude.add(middleware)
            amplitude.timeline.plugins[Plugin.Type.Enrichment]?.size()?.let {
                assertEquals(
                    2,
                    it,
                )
            } ?: fail()
        }

        @Test
        fun `Can remove plugins from amplitude`() {
            val middleware =
                object : Plugin {
                    override val type = Plugin.Type.Enrichment
                    override lateinit var amplitude: Amplitude
                }
            amplitude.add(middleware)
            amplitude.remove(middleware)
            amplitude.timeline.plugins[Plugin.Type.Enrichment]?.size()?.let {
                assertEquals(
                    1,
                    it,
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

    @Nested
    inner class TestDefensiveCopy {
        @Test
        fun `track with BaseEvent copies event property maps`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")

            val originalEventProps = mutableMapOf<String, Any?>("key1" to "value1")
            val originalUserProps = mutableMapOf<String, Any?>("ukey" to "uval")
            val originalGroups = mutableMapOf<String, Any?>("gtype" to "gname")
            val originalGroupProps = mutableMapOf<String, Any?>("gpkey" to "gpval")

            val event =
                BaseEvent().apply {
                    eventType = "test event"
                    eventProperties = originalEventProps
                    userProperties = originalUserProps
                    groups = originalGroups
                    groupProperties = originalGroupProps
                }

            amplitude.track(event)

            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }

            // The event's maps should be different instances from the originals
            assertNotSame(originalEventProps, track.captured.eventProperties)
            assertNotSame(originalUserProps, track.captured.userProperties)
            assertNotSame(originalGroups, track.captured.groups)
            assertNotSame(originalGroupProps, track.captured.groupProperties)
        }

        @Test
        fun `mutations after track do not affect event in pipeline`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")

            val originalProps = mutableMapOf<String, Any?>("key1" to "value1")
            val event =
                BaseEvent().apply {
                    eventType = "test event"
                    eventProperties = originalProps
                }

            amplitude.track(event)

            // Mutate the original map after track (simulating user code on Dispatchers.IO)
            originalProps["key2"] = "value2"
            originalProps.remove("key1")

            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }

            // Plugin should see the original snapshot, not the mutations
            assertEquals(mapOf("key1" to "value1"), track.captured.eventProperties)
        }

        @Test
        fun `concurrent serialization on different thread does not crash`() {
            // Simulates the AmplitudeEngagementPlugin scenario:
            // The destination plugin dispatches event serialization to the Main thread,
            // while user code concurrently modifies the original properties map.
            val errors = Collections.synchronizedList(mutableListOf<Throwable>())
            val serializationStarted = CountDownLatch(1)
            val serializationComplete = CountDownLatch(1)

            val asyncPlugin =
                object : DestinationPlugin() {
                    override fun track(payload: BaseEvent): BaseEvent? {
                        // Simulate engagement plugin dispatching to Main thread
                        thread {
                            try {
                                serializationStarted.countDown()
                                JSONUtil.eventToJsonObject(payload)
                            } catch (e: Throwable) {
                                errors.add(e)
                            } finally {
                                serializationComplete.countDown()
                            }
                        }
                        return payload
                    }
                }

            amplitude.add(asyncPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")

            val sharedMap = mutableMapOf<String, Any?>("initial" to "value")
            val event =
                BaseEvent().apply {
                    eventType = "test event"
                    eventProperties = sharedMap
                }

            amplitude.track(event)

            // Wait for the serialization thread to start
            assertTrue(serializationStarted.await(5, TimeUnit.SECONDS))

            // Concurrently modify the original map (simulating user code on Dispatchers.IO)
            repeat(1000) { i ->
                sharedMap["key_$i"] = "value_$i"
            }

            assertTrue(serializationComplete.await(5, TimeUnit.SECONDS))

            assertTrue(
                errors.isEmpty(),
                "Expected no errors but got: ${errors.map { "${it.javaClass.simpleName}: ${it.message}" }}",
            )
        }

        @Test
        fun `nested mutable maps are deep-copied`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)
            amplitude.setUserId("user_id")
            amplitude.setDeviceId("device_id")

            val nestedMap = mutableMapOf<String, Any?>("inner" to "original")
            val originalProps = mutableMapOf<String, Any?>("nested" to nestedMap)

            val event =
                BaseEvent().apply {
                    eventType = "test event"
                    eventProperties = originalProps
                }

            amplitude.track(event)

            // Mutate the nested map after track
            nestedMap["inner"] = "mutated"
            nestedMap["new_key"] = "new_value"

            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }

            // The nested map in the pipeline should be unaffected
            val capturedNested = track.captured.eventProperties?.get("nested") as Map<*, *>
            assertEquals("original", capturedNested["inner"])
            assertNull(capturedNested["new_key"])
        }

        @Test
        fun `enrichment plugin dispatching to another thread races with subsequent enrichment plugin`() {
            // Reproduces AMP-150851: ConcurrentModificationException with AmplitudeEngagementPlugin.
            //
            // The engagement plugin (Enrichment) fire-and-forgets event serialization to Dispatchers.Main
            // via: CoroutineScope(Dispatchers.Main).launch { amplitudeEngagement.forwardEvent(event) }
            //
            // The pipeline continues on its own thread to the NEXT enrichment plugin, which modifies
            // the same event's maps (e.g., customer's ad-ID plugin adding to userProperties).
            //
            // Race: Main thread iterates maps in toJSONObject() while pipeline thread mutates them.
            //
            // Customer setup:
            //   1. AmplitudeEngagementPlugin (Enrichment) — dispatches forwardEvent(event) to Main
            //   2. Custom ad-ID plugin (Enrichment) — sets userProperties["advertisingId"]
            //   Both share the same event object. The deep copy in process() doesn't help because
            //   it's done BEFORE the event enters the pipeline — all plugins still share the same maps.

            val errors = Collections.synchronizedList(mutableListOf<Throwable>())
            val barrier = java.util.concurrent.CyclicBarrier(2)
            val writerRunning = java.util.concurrent.atomic.AtomicBoolean(true)
            val done = CountDownLatch(2)

            // Simulate the deep-copied event that process() creates
            val event =
                BaseEvent().apply {
                    eventType = "test_event"
                    eventProperties = (1..500).associate { "ep_$it" to "val_$it" }.toMutableMap()
                    userProperties = (1..500).associate { "up_$it" to "val_$it" }.toMutableMap()
                }

            // Thread 1: simulates engagement plugin serializing on Dispatchers.Main
            thread {
                barrier.await()
                try {
                    repeat(500) {
                        try {
                            JSONUtil.eventToJsonObject(event)
                        } catch (e: ConcurrentModificationException) {
                            errors.add(e)
                        }
                    }
                } finally {
                    writerRunning.set(false)
                    done.countDown()
                }
            }

            // Thread 2: simulates next enrichment plugin modifying maps on pipeline thread.
            // Every put() uses a unique key so it's a structural modification (increments modCount),
            // which is what triggers ConcurrentModificationException on the iterator.
            thread {
                barrier.await()
                try {
                    var i = 0
                    while (writerRunning.get()) {
                        event.userProperties?.put("ad_key_$i", "ad_val_$i")
                        event.eventProperties?.put("ep_key_$i", "ep_val_$i")
                        i++
                    }
                } finally {
                    done.countDown()
                }
            }

            assertTrue(done.await(10, TimeUnit.SECONDS))

            // CME occurs because the engagement plugin's fire-and-forget serialization on
            // Main races with subsequent plugin mutations on the pipeline thread.
            //
            // The fix belongs in the engagement plugin: serialize BEFORE dispatching to Main.
            // This way serialization and modification are sequential on the pipeline thread,
            // and only the independent JSONObject crosses the thread boundary.
            assertTrue(
                errors.any { it is ConcurrentModificationException },
                "Expected ConcurrentModificationException demonstrating the pipeline-internal race, " +
                    "but got: ${errors.map { "${it.javaClass.simpleName}: ${it.message}" }}",
            )
        }
    }

    @Nested
    inner class TestIdentityOrdering {
        @Test
        fun `setUserId then track - event has new userId`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)

            amplitude.setUserId("new-user")
            amplitude.track("test_event")

            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }
            assertEquals("new-user", track.captured.userId)
        }

        @Test
        fun `setDeviceId then track - event has new deviceId`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)

            amplitude.setDeviceId("custom-device")
            amplitude.track("test_event")

            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }
            assertEquals("custom-device", track.captured.deviceId)
        }

        @Test
        fun `reset then track - event has null userId and new deviceId`() {
            val mockPlugin = spyk(StubPlugin())
            amplitude.add(mockPlugin)

            amplitude.setUserId("old-user")
            amplitude.setDeviceId("old-device")
            amplitude.reset()
            amplitude.track("test_event")

            val track = slot<BaseEvent>()
            verify { mockPlugin.track(capture(track)) }
            track.captured.let {
                assertEquals(null, it.userId)
                assertNotEquals("old-device", it.deviceId)
            }
        }
    }
}
