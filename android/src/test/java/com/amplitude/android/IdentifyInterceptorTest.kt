package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Identify
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.toEvents
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class IdentifyInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var amplitude: Amplitude

    @ExperimentalCoroutinesApi
    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockContextProvider()

        val apiKey = "test-api-key"
        amplitude = Amplitude(apiKey, context) {
            this.serverUrl = server.url("/").toString()
            @Suppress("DEPRECATION")
            this.trackingSessionEvents = false
            this.flushIntervalMillis = 1000
            this.identifyBatchIntervalMillis = 1000
        }
    }

    @After
    fun shutdown() {
        server.shutdown()
    }

    @Test
    fun `test multiple identify with set action only send one identify event`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))

        val request = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(1, events.size)
        val expectedUserProperties = mapOf(
            "key1" to "key1-value2",
            "key2" to "key2-value2",
            "key3" to "key3-value2",
            "key4" to "key4-value2"
        )
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertEquals(
            expectedUserProperties,
            events[0].userProperties?.get(
                IdentifyOperation.SET.operationType
            )
        )
    }

    @Test
    fun `test multiple identify with set action only and one event`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
        val testEvent = BaseEvent()
        testEvent.eventType = "test_event"
        testEvent.userProperties = mutableMapOf("test_key" to "test_value", "key1" to "key1-value3", "key2" to "key2-value3")
        amplitude.track(testEvent)
        val request: RecordedRequest? = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(2, events.size)
        val expectedUserProperties1 = mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertEquals(expectedUserProperties1, events[0].userProperties?.get(IdentifyOperation.SET.operationType))
        val expectedUserProperties2 = mapOf("key1" to "key1-value3", "key2" to "key2-value3", "test_key" to "test_value")
        assertEquals("test_event", events[1].eventType)
        assertEquals(expectedUserProperties2, events[1].userProperties)
    }

    @Test
    fun `test multiple identify with set action only and one event and identify`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
        val testEvent = BaseEvent()
        testEvent.eventType = "test_event"
        testEvent.userProperties = mutableMapOf("test_key" to "test_value", "key1" to "key1-value3", "key2" to "key2-value3")
        amplitude.track(testEvent)
        amplitude.identify(mapOf("key1" to "key1-value4"))
        val request: RecordedRequest? = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(3, events.size)
        val expectedUserProperties1 = mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertEquals(expectedUserProperties1, events[0].userProperties?.get(IdentifyOperation.SET.operationType))
        val expectedUserProperties2 = mapOf("key1" to "key1-value3", "key2" to "key2-value3", "test_key" to "test_value")
        assertEquals("test_event", events[1].eventType)
        assertEquals(expectedUserProperties2, events[1].userProperties)
        val expectedUserProperties3 = mapOf("key1" to "key1-value4")
        assertEquals(Constants.IDENTIFY_EVENT, events[2].eventType)
        assertEquals(expectedUserProperties3, events[2].userProperties?.get(IdentifyOperation.SET.operationType))
    }

    @Test
    fun `test multiple identify with set action and clear all`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
        amplitude.identify(Identify().clearAll())
        val request: RecordedRequest? = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(1, events.size)
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertTrue(events[0].userProperties!!.containsKey(IdentifyOperation.CLEAR_ALL.operationType))
        assertFalse(events[0].userProperties!!.containsKey(IdentifyOperation.SET.operationType))
    }

    @Test
    fun `test multiple identify with set action and another identify`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
        amplitude.identify(Identify().add("key5", 2))
        val request: RecordedRequest? = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(2, events.size)
        val expectedUserProperties = mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertEquals(
            expectedUserProperties,
            events[0].userProperties?.get(IdentifyOperation.SET.operationType)
        )
        assertEquals(Constants.IDENTIFY_EVENT, events[1].eventType)
        assertEquals(
            mapOf("key5" to 2),
            events[1].userProperties?.get(IdentifyOperation.ADD.operationType)
        )
    }

    @Test
    fun `test flush send intercepted identify`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
        amplitude.flush()
        val request: RecordedRequest? = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(1, events.size)
        val expectedUserProperties = mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertEquals(
            expectedUserProperties,
            events[0].userProperties?.get(IdentifyOperation.SET.operationType)
        )
    }

    @Test
    fun `test multiple identify with set action only and set group and identify`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
        amplitude.setGroup("test-group-type", "test-group-value")
        amplitude.identify(mapOf("key3" to "key3-value3", "key4" to "key4-value3"))
        val request: RecordedRequest? = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(3, events.size)
        val expectedUserProperties = mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertEquals(
            expectedUserProperties,
            events[0].userProperties?.get(IdentifyOperation.SET.operationType)
        )

        val expectedUserProperties1 = mapOf("test-group-type" to "test-group-value")
        assertEquals(Constants.IDENTIFY_EVENT, events[1].eventType)
        assertEquals(
            expectedUserProperties1,
            events[1].userProperties?.get(IdentifyOperation.SET.operationType)
        )
        assertEquals(mapOf("test-group-type" to "test-group-value"), events[1].groups)

        val expectedUserProperties2 = mapOf("key3" to "key3-value3", "key4" to "key4-value3")
        assertEquals(Constants.IDENTIFY_EVENT, events[2].eventType)
        assertEquals(
            expectedUserProperties2,
            events[2].userProperties?.get(IdentifyOperation.SET.operationType)
        )
    }

    @Test
    fun `test multiple identify with user id update`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
        amplitude.amplitudeScope.launch(amplitude.amplitudeDispatcher) {
            sleep(400)
            val options = EventOptions().apply {
                this.userId = "identify_user_id"
            }
            amplitude.identify(mapOf("key3" to "key3-value3", "key4" to "key4-value3"), options)
        }
        val request: RecordedRequest? = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(2, events.size)
        val expectedUserProperties = mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertEquals(
            expectedUserProperties,
            events[0].userProperties?.get(IdentifyOperation.SET.operationType)
        )
        assertNull(events[0].userId)
        val expectedUserProperties2 = mapOf("key3" to "key3-value3", "key4" to "key4-value3")
        assertEquals(Constants.IDENTIFY_EVENT, events[1].eventType)
        assertEquals(
            expectedUserProperties2,
            events[1].userProperties?.get(IdentifyOperation.SET.operationType)
        )
        assertEquals("identify_user_id", events[1].userId)
    }

    @Test
    fun `test null user properties filtered out`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
        amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
        amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
        amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
        amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
        val testEvent = BaseEvent()
        testEvent.eventType = "test_event"
        testEvent.userProperties = mutableMapOf("key1" to null, "key2" to null)
        amplitude.flush()
        val request: RecordedRequest? = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(1, events.size)
        val expectedUserProperties = mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
        assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
        assertEquals(
            expectedUserProperties,
            events[0].userProperties?.get(IdentifyOperation.SET.operationType)
        )
    }

    private fun getEventsFromRequest(request: RecordedRequest): List<BaseEvent> {
        val body = request.body.readUtf8()
        return JSONObject(body).getJSONArray("events").toEvents()
    }

    private fun runRequest(): RecordedRequest? {
        return try {
            server.takeRequest(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            null
        }
    }

    private fun mockContextProvider() {
        mockkStatic(AndroidLifecyclePlugin::class)

        mockkConstructor(AndroidContextProvider::class)
        every { anyConstructed<AndroidContextProvider>().osName } returns "android"
        every { anyConstructed<AndroidContextProvider>().osVersion } returns "10"
        every { anyConstructed<AndroidContextProvider>().brand } returns "google"
        every { anyConstructed<AndroidContextProvider>().manufacturer } returns "Android"
        every { anyConstructed<AndroidContextProvider>().model } returns "Android SDK built for x86"
        every { anyConstructed<AndroidContextProvider>().language } returns "English"
        every { anyConstructed<AndroidContextProvider>().advertisingId } returns ""
        every { anyConstructed<AndroidContextProvider>().versionName } returns "1.0"
        every { anyConstructed<AndroidContextProvider>().carrier } returns "Android"
        every { anyConstructed<AndroidContextProvider>().country } returns "US"
        every { anyConstructed<AndroidContextProvider>().mostRecentLocation } returns null
        every { anyConstructed<AndroidContextProvider>().appSetId } returns ""
    }
}
