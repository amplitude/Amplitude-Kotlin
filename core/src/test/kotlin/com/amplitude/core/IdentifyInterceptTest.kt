package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.Identify
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.toEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentifyInterceptTest {
    private lateinit var server: MockWebServer
    private lateinit var amplitude: Amplitude

    @ExperimentalCoroutinesApi
    private lateinit var testScope: TestScope

    @ExperimentalCoroutinesApi
    private lateinit var testDispatcher: TestDispatcher

    @ExperimentalCoroutinesApi
    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        val testApiKey = "test-123"
        val plan = Plan("test-branch", "test")
        val ingestionMetadata = IngestionMetadata("ampli", "2.0.0")
        testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        amplitude =
            Amplitude(
                Configuration(testApiKey, plan = plan, ingestionMetadata = ingestionMetadata, serverUrl = server.url("/").toString()),
                State(),
                testScope,
                testDispatcher,
                testDispatcher,
                testDispatcher,
            )
    }

    @AfterEach
    fun shutdown() {
        server.shutdown()
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `test multiple identify with set action only send one identify event`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
            amplitude.setUserId("user_id")
            advanceUntilIdle()
            amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
            amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
            amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
            amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
            advanceUntilIdle()
            val request: RecordedRequest? = runRequest()
            assertNotNull(request)
            val events = getEventsFromRequest(request!!)
            assertEquals(1, events.size)
            val expectedUserProperties =
                mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
            assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
            assertEquals(expectedUserProperties, events[0].userProperties?.get(IdentifyOperation.SET.operationType))
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `test multiple identify with set action only and one event`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
            amplitude.setUserId("user_id")
            advanceUntilIdle()
            amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
            amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
            amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
            amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
            val testEvent = BaseEvent()
            testEvent.eventType = "test_event"
            testEvent.userProperties = mutableMapOf("test_key" to "test_value", "key1" to "key1-value3", "key2" to "key2-value3")
            amplitude.track(testEvent)
            advanceUntilIdle()
            val request: RecordedRequest? = runRequest()
            assertNotNull(request)
            val events = getEventsFromRequest(request!!)
            assertEquals(2, events.size)
            val expectedUserProperties1 =
                mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
            assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
            assertEquals(
                expectedUserProperties1,
                events[0].userProperties?.get(IdentifyOperation.SET.operationType),
            )
            val expectedUserProperties2 = mapOf("key1" to "key1-value3", "key2" to "key2-value3", "test_key" to "test_value")
            assertEquals("test_event", events[1].eventType)
            assertEquals(expectedUserProperties2, events[1].userProperties)
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `test multiple identify with set action only and one event and identify`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
            amplitude.setUserId("user_id")
            advanceUntilIdle()
            amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
            amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
            amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
            amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
            val testEvent = BaseEvent()
            testEvent.eventType = "test_event"
            testEvent.userProperties = mutableMapOf("test_key" to "test_value", "key1" to "key1-value3", "key2" to "key2-value3")
            amplitude.track(testEvent)
            amplitude.identify(mapOf("key1" to "key1-value4"))
            advanceUntilIdle()
            val request: RecordedRequest? = runRequest()
            assertNotNull(request)
            val events = getEventsFromRequest(request!!)
            assertEquals(3, events.size)
            val expectedUserProperties1 =
                mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
            assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
            assertEquals(
                expectedUserProperties1,
                events[0].userProperties?.get(IdentifyOperation.SET.operationType),
            )
            val expectedUserProperties2 = mapOf("key1" to "key1-value3", "key2" to "key2-value3", "test_key" to "test_value")
            assertEquals("test_event", events[1].eventType)
            assertEquals(expectedUserProperties2, events[1].userProperties)
            val expectedUserProperties3 = mapOf("key1" to "key1-value4")
            assertEquals(Constants.IDENTIFY_EVENT, events[2].eventType)
            assertEquals(
                expectedUserProperties3,
                events[2].userProperties?.get(IdentifyOperation.SET.operationType),
            )
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `test multiple identify with set action and clear all`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
            amplitude.setUserId("user_id")
            advanceUntilIdle()
            amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
            amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
            amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
            amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
            amplitude.identify(Identify().clearAll())
            advanceUntilIdle()
            val request: RecordedRequest? = runRequest()
            assertNotNull(request)
            val events = getEventsFromRequest(request!!)
            assertEquals(1, events.size)
            assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
            assertTrue(events[0].userProperties!!.containsKey(IdentifyOperation.CLEAR_ALL.operationType))
            assertFalse(events[0].userProperties!!.containsKey(IdentifyOperation.SET.operationType))
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `test multiple identify with set action and another identify`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
            amplitude.setUserId("user_id")
            advanceUntilIdle()
            amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
            amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
            amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
            amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
            amplitude.identify(Identify().add("key5", 2))
            advanceUntilIdle()
            val request: RecordedRequest? = runRequest()
            assertNotNull(request)
            val events = getEventsFromRequest(request!!)
            assertEquals(2, events.size)
            val expectedUserProperties =
                mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
            assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
            assertEquals(expectedUserProperties, events[0].userProperties?.get(IdentifyOperation.SET.operationType))
            assertEquals(Constants.IDENTIFY_EVENT, events[1].eventType)
            assertEquals(mapOf("key5" to 2), events[1].userProperties?.get(IdentifyOperation.ADD.operationType))
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `test flush send intercepted identify`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
            amplitude.setUserId("user_id")
            advanceUntilIdle()
            amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
            amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
            amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
            amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
            amplitude.flush()
            advanceUntilIdle()
            val request: RecordedRequest? = runRequest()
            assertNotNull(request)
            val events = getEventsFromRequest(request!!)
            assertEquals(1, events.size)
            val expectedUserProperties =
                mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
            assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
            assertEquals(expectedUserProperties, events[0].userProperties?.get(IdentifyOperation.SET.operationType))
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `test multiple identify with set action only and set group and identify`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
            amplitude.setUserId("user_id")
            advanceUntilIdle()
            amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
            amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
            amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
            amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
            amplitude.setGroup("test-group-type", "test-group-value")
            amplitude.identify(mapOf("key3" to "key3-value3", "key4" to "key4-value3"))
            advanceUntilIdle()
            val request: RecordedRequest? = runRequest()
            assertNotNull(request)
            val events = getEventsFromRequest(request!!)
            assertEquals(3, events.size)
            val expectedUserProperties =
                mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
            assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
            assertEquals(expectedUserProperties, events[0].userProperties?.get(IdentifyOperation.SET.operationType))

            val expectedUserProperties1 = mapOf("test-group-type" to "test-group-value")
            assertEquals(Constants.IDENTIFY_EVENT, events[1].eventType)
            assertEquals(expectedUserProperties1, events[1].userProperties?.get(IdentifyOperation.SET.operationType))
            assertEquals(mapOf("test-group-type" to "test-group-value"), events[1].groups)

            val expectedUserProperties2 = mapOf("key3" to "key3-value3", "key4" to "key4-value3")
            assertEquals(Constants.IDENTIFY_EVENT, events[2].eventType)
            assertEquals(expectedUserProperties2, events[2].userProperties?.get(IdentifyOperation.SET.operationType))
        }

    @ExperimentalCoroutinesApi
    @Test
    fun `test multiple identify with user id update`() =
        runTest(testDispatcher) {
            server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))
            amplitude.setUserId("user_id")
            advanceUntilIdle()
            amplitude.identify(Identify().set("key1", "key1-value1").set("key2", "key2-value1"))
            amplitude.identify(Identify().set("key3", "key3-value1").set("key1", "key1-value2"))
            amplitude.identify(Identify().set("key4", "key4-value1").set("key2", "key2-value2"))
            amplitude.identify(Identify().set("key3", "key3-value2").set("key4", "key4-value2"))
            amplitude.setUserId("identify_user_id")
            advanceTimeBy(100L)
            amplitude.identify(mapOf("key3" to "key3-value3", "key4" to "key4-value3"))
            advanceUntilIdle()
            val request: RecordedRequest? = runRequest()
            assertNotNull(request)
            val events = getEventsFromRequest(request!!)
            assertEquals(2, events.size)
            val expectedUserProperties =
                mapOf("key1" to "key1-value2", "key2" to "key2-value2", "key3" to "key3-value2", "key4" to "key4-value2")
            assertEquals(Constants.IDENTIFY_EVENT, events[0].eventType)
            assertEquals(expectedUserProperties, events[0].userProperties?.get(IdentifyOperation.SET.operationType))
            assertEquals("user_id", events[0].userId)
            val expectedUserProperties2 = mapOf("key3" to "key3-value3", "key4" to "key4-value3")
            assertEquals(Constants.IDENTIFY_EVENT, events[1].eventType)
            assertEquals(expectedUserProperties2, events[1].userProperties?.get(IdentifyOperation.SET.operationType))
            assertEquals("identify_user_id", events[1].userId)
        }

    private fun getEventsFromRequest(request: RecordedRequest): List<BaseEvent> {
        val body = request.body.readUtf8()
        return JSONObject(body).getJSONArray("events").toEvents()
    }

    private fun runRequest(): RecordedRequest? {
        return try {
            server.takeRequest(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            null
        }
    }
}
