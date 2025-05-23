package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.events.EventOptions
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.Configuration.Companion.FLUSH_MAX_RETRIES
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.HttpStatus
import com.amplitude.core.utilities.toEvents
import com.amplitude.id.IMIdentityStorageProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ResponseHandlerIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var amplitude: Amplitude

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun shutdown() {
        server.shutdown()
    }

    @Test
    fun `test handle on success`() = runTest {
        amplitude = createFakeAmplitude(server)
        var eventCompleteCount = 0
        val statusMap = mutableMapOf<Int, Int>()
        val options = EventOptions()
        options.callback = { _: BaseEvent, status: Int, _: String ->
            eventCompleteCount++
            statusMap[status] = statusMap.getOrDefault(status, 0) + 1
        }
        amplitude.isBuilt.await()

        // verify that it will succeed
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        amplitude.track("test event 1", options = options)
        amplitude.track("test event 2", options = options)
        advanceUntilIdle()
        val request = runRequest()
        requireNotNull(request)
        val events = getEventsFromRequest(request)

        assertEquals(2, events.size)
        assertEquals(1, server.requestCount)
        Thread.sleep(100)

        // verify events covered with correct response
        assertEquals(2, statusMap[200])
        assertEquals(2, eventCompleteCount)
    }

    @Test
    fun `test handle on rate limit`() = runTest {
        amplitude = createFakeAmplitude(server)
        val expectedEventsSize = FLUSH_MAX_RETRIES
        val rateLimitBody = """
        {
          "code": 429,
          "error": "Too many requests for some devices and users",
          "eps_threshold": 30
        }
        """.trimIndent()
        repeat(FLUSH_MAX_RETRIES) {
            server.enqueue(MockResponse().setBody(rateLimitBody).setResponseCode(429))
        }
        server.enqueue(MockResponse().setResponseCode(200))

        amplitude.isBuilt.await()
        for (k in 1..expectedEventsSize) {
            amplitude.track("test event $k")
        }
        advanceUntilIdle()

        // verify the total request count when reaching max retries
        assertEquals(1 + FLUSH_MAX_RETRIES, server.requestCount)
    }

    @Test
    fun `test handle payload too large with only one event`() = runTest {
        amplitude = createFakeAmplitude(server)
        val options = EventOptions()
        var statusCode = 0
        var callFinished = false
        options.callback = { _: BaseEvent, status: Int, _: String ->
            statusCode = status
            callFinished = true
        }
        amplitude.isBuilt.await()

        // verify it will attempt to retry, but single event file will be removed
        val code = HttpStatus.PAYLOAD_TOO_LARGE.statusCode
        val body = "{\"code\": \"$code\", \"error\": \"payload too large\"}"
        server.enqueue(MockResponse().setBody(body).setResponseCode(code))
        amplitude.track("test event 1", options = options)
        advanceUntilIdle()
        val request = runRequest()

        // verify we send only one request
        requireNotNull(request)
        val events = getEventsFromRequest(request)
        assertEquals(1, events.size)
        assertEquals(1, server.requestCount)

        // verify we remove the large event
        assertEquals(413, statusCode)
        assertTrue(callFinished)
    }

    @Test
    fun `test handle payload too large`() = runTest {
        amplitude = createFakeAmplitude(
            server = server,
            scheduler = testScheduler,
            configuration = Configuration(
                flushQueueSize = 2, // required for in memory storage testing
                apiKey = "test-api-key",
                context = ApplicationProvider.getApplicationContext<Context?>()
                    .also { setupMockAndroidContext() },
                serverUrl = server.url("/").toString(),
                autocapture = setOf(),
                flushIntervalMillis = 150,
                identifyBatchIntervalMillis = 1000,
                flushMaxRetries = FLUSH_MAX_RETRIES,
                identityStorageProvider = IMIdentityStorageProvider(),
                storageProvider = InMemoryStorageProvider()
            )
        )
        val expectedSuccessEvents = 5
        server.enqueue(
            MockResponse().setBody("{\"code\": \"413\", \"error\": \"payload too large\"}")
                .setResponseCode(413)
        )
        repeat(expectedSuccessEvents) {
            server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        }
        var eventsProcessed = 0
        val statusMap = mutableMapOf<Int, Int>()
        val options = EventOptions()
        options.callback = { _: BaseEvent, status: Int, _: String ->
            eventsProcessed++
            statusMap[status] = statusMap.getOrDefault(status, 0) + 1
        }

        // send 2 events so the event size will be greater than 1 and increment flushSizeDivider (or call split event file)
        amplitude.isBuilt.await()
        amplitude.track("test event 1", options = options)
        amplitude.track("test event 2", options = options)
        advanceUntilIdle()

        // verify first request that hit 413
        val request = runRequest()
        requireNotNull(request)
        val failedEvents = getEventsFromRequest(request)
        assertEquals(2, failedEvents.size)

        // verify next requests after split event file
        val splitRequest1 = runRequest()
        requireNotNull(splitRequest1)
        val splitEvents1 = getEventsFromRequest(splitRequest1)
        assertEquals(1, splitEvents1.size)
        assertEquals("test event 1", splitEvents1.first().eventType)
        val splitRequest2 = runRequest()
        requireNotNull(splitRequest2)
        val splitEvents2 = getEventsFromRequest(splitRequest2)
        assertEquals(1, splitEvents2.size)
        assertEquals("test event 2", splitEvents2.first().eventType)

        // send succeeding events
        amplitude.track("test event 3", options = options)
        amplitude.track("test event 4", options = options)
        amplitude.track("test event 5", options = options)
        advanceUntilIdle()

        // verify we completed processing for the events after file split
        val afterSplitRequest = runRequest()
        requireNotNull(afterSplitRequest)
        val afterSplitEvents = getEventsFromRequest(afterSplitRequest)
        assertEquals(3, afterSplitEvents.size)

        // verify we completed processing for the events after file split
        assertEquals(4, server.requestCount)
        assertEquals(expectedSuccessEvents, statusMap[200])
        assertEquals(expectedSuccessEvents, eventsProcessed)
    }

    @Test
    fun `test handle bad request response - missing field`() = runTest {
        amplitude = createFakeAmplitude(server)
        val badRequestResponseBody = """
        {
          "code": 400,
          "error": "Request missing required field",
          "events_with_invalid_fields": {
            "time": [
              3
            ]
          },
          "events_with_missing_fields": {
            "event_type": [
              0
            ]
          }
        }
        """.trimIndent()
        server.enqueue(MockResponse().setBody(badRequestResponseBody).setResponseCode(400))
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        var eventCompleteCount = 0
        val statusMap = mutableMapOf<Int, Int>()
        val options = EventOptions()
        options.callback = { _: BaseEvent, status: Int, _: String ->
            eventCompleteCount++
            statusMap[status] = statusMap.getOrDefault(status, 0) + 1
        }

        amplitude.isBuilt.await()
        amplitude.track("test event 1", options = options)
        advanceUntilIdle()

        // verify first request that hit 400
        val request = runRequest()
        requireNotNull(request)
        val failedEvents = getEventsFromRequest(request)
        assertEquals(1, failedEvents.size)

        // send succeeding events
        amplitude.track("test event 2", options = options)
        amplitude.track("test event 3", options = options)
        amplitude.track("test event 4", options = options)
        advanceUntilIdle()

        // verify next request that the 3 events with 200
        val successRequest = runRequest()
        requireNotNull(successRequest)
        val successfulEvents = getEventsFromRequest(successRequest)
        assertEquals(3, successfulEvents.size)

        assertEquals(2, server.requestCount)
        Thread.sleep(10)

        // verify the processed status
        assertEquals(1, statusMap[400])
        assertEquals(3, statusMap[200])
        assertEquals(4, eventCompleteCount)
    }

    @Test
    fun `test handle bad request response retry`() = runTest {
        testRequestFailureAndRetry(HttpStatus.BAD_REQUEST.statusCode)
    }

    @Test
    fun `test handle too many requests response retry`() = runTest {
        testRequestFailureAndRetry(HttpStatus.TOO_MANY_REQUESTS.statusCode)
    }

    @Test
    fun `test handle timeout response retry`() = runTest {
        testRequestFailureAndRetry(HttpStatus.TIMEOUT.statusCode)
    }

    @Test
    fun `test handle failed response retry`() = runTest {
        testRequestFailureAndRetry(HttpStatus.FAILED.statusCode)
    }

    private suspend fun TestScope.testRequestFailureAndRetry(code: Int) {
        amplitude = createFakeAmplitude(server)
        var eventsProcessed = 0
        val statusMap = mutableMapOf<Int, Int>()
        val options = EventOptions()
        options.callback = { _: BaseEvent, status: Int, _: String ->
            eventsProcessed++
            statusMap[status] = statusMap.getOrDefault(status, 0) + 1
        }
        amplitude.isBuilt.await()

        // verify that it will retry
        server.enqueue(MockResponse().setBody("{\"code\": \"$code\"}").setResponseCode(code))
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        amplitude.track("test event 1", options = options)
        advanceUntilIdle()
        val requests = buildList {
            add(runRequest()!!)
            add(runRequest()!!)
        }
        assertEquals(2, requests.size)
        requests.forEach {
            val events = getEventsFromRequest(it)
            assertTrue(events.size == 1)
            val event = events.first()
            assertEquals(event.eventType, "test event 1")
        }

        // verify that succeeding events will succeed
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        amplitude.track("test event 2", options = options)
        advanceUntilIdle()
        val request = runRequest()
        requireNotNull(request)
        val event = getEventsFromRequest(request).first()
        assertEquals(event.eventType, "test event 2")

        // verify total count of requests and statuses, and events sent
        assertEquals(3, server.requestCount)
        assertEquals(2, statusMap[200])
        assertEquals(2, eventsProcessed)
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
