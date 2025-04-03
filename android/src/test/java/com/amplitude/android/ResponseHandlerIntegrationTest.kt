package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.events.EventOptions
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.toEvents
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

private const val FLUSH_INTERVAL_IN_MS = 150
private const val FLUSH_MAX_RETRIES = 3

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ResponseHandlerIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var amplitude: Amplitude

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        mockContextProvider()

        val apiKey = "test-api-key"
        amplitude = Amplitude(
            Configuration(
                apiKey = apiKey,
                context = context,
                serverUrl = server.url("/").toString(),
                autocapture = setOf(),
                flushIntervalMillis = FLUSH_INTERVAL_IN_MS,
                identifyBatchIntervalMillis = 1000,
                flushMaxRetries = FLUSH_MAX_RETRIES,
                identityStorageProvider = IMIdentityStorageProvider(),
            )
        )
    }

    @After
    fun shutdown() {
        server.shutdown()
    }

    @Test
    fun `test handle on success`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        var eventCompleteCount = 0
        val statusMap = mutableMapOf<Int, Int>()
        val options = EventOptions()
        options.callback = { _: BaseEvent, status: Int, _: String ->
            eventCompleteCount++
            statusMap.put(status, statusMap.getOrDefault(status, 0) + 1)
        }
        amplitude.track("test event 1", options = options)
        amplitude.track("test event 2", options = options)
        val request = runRequest()
        // verify request
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(2, events.size)
        assertEquals(1, server.requestCount)
        Thread.sleep(100)
        // verify events covered with correct response
        assertEquals(2, statusMap.get(200))
        assertEquals(2, eventCompleteCount)
    }

    @Test
    fun `test handle on rate limit`() = runTest {
        setAmplitudeDispatchers(amplitude, testScheduler)
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
        server.enqueue(
            MockResponse().setBody("{\"code\": \"413\", \"error\": \"payload too large\"}")
                .setResponseCode(413)
        )
        val options = EventOptions()
        var statusCode = 0
        var callFinished = false
        options.callback = { _: BaseEvent, status: Int, _: String ->
            statusCode = status
            callFinished = true
        }
        amplitude.track("test event 1", options = options)
        val request = runRequest()
        // verify we send only one request
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(1, events.size)
        assertEquals(1, server.requestCount)
        Thread.sleep(100)
        // verify we remove the large event
        assertEquals(413, statusCode)
        assertTrue(callFinished)
    }

    @Test
    fun `test handle payload too large`() = runTest {
        setAmplitudeDispatchers(amplitude, testScheduler)
        val expectedSuccesEvents = 5
        server.enqueue(
            MockResponse().setBody("{\"code\": \"413\", \"error\": \"payload too large\"}")
                .setResponseCode(413)
        )
        repeat(expectedSuccesEvents) {
            server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        }
        var eventCompleteCount = 0
        val statusMap = mutableMapOf<Int, Int>()
        val options = EventOptions()
        options.callback = { _: BaseEvent, status: Int, _: String ->
            eventCompleteCount++
            statusMap[status] = statusMap.getOrDefault(status, 0) + 1
        }

        // send 2 events so the event size will be greater than 1 and call split event file
        amplitude.isBuilt.await()
        amplitude.track("test event 1", options = options)
        amplitude.track("test event 2", options = options)
        advanceTimeBy(1_000)

        // verify first request that hit 413
        val request = runRequest()
        requireNotNull(request)
        val failedEvents = getEventsFromRequest(request)
        assertEquals(2, failedEvents.size)

        // send succeeding events
        amplitude.track("test event 3", options = options)
        amplitude.track("test event 4", options = options)
        amplitude.track("test event 5", options = options)
        advanceUntilIdle()

        // verify next requests after split event file
        val splitRequest1 = runRequest()
        requireNotNull(splitRequest1)
        val splitEvents1 = getEventsFromRequest(splitRequest1)
        assertEquals(1, splitEvents1.size)
        val splitRequest2 = runRequest()
        requireNotNull(splitRequest2)
        val splitEvents2 = getEventsFromRequest(splitRequest2)
        assertEquals(1, splitEvents2.size)

        // verify we completed processing for the events after file split
        val afterSplitRequest = runRequest()
        requireNotNull(afterSplitRequest)
        val afterSplitEvents = getEventsFromRequest(afterSplitRequest)
        assertEquals(3, afterSplitEvents.size)

        // verify we completed processing for the events after file split
        assertEquals(4, server.requestCount)
        assertEquals(expectedSuccesEvents, statusMap[200])
        assertEquals(expectedSuccesEvents, eventCompleteCount)
    }

    @Test
    fun `test handle bad request response - missing field`() = runTest {
        setAmplitudeDispatchers(amplitude, testScheduler)
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
    fun `test handle timeout response`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"408\"}").setResponseCode(408))
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        var eventCompleteCount = 0
        val statusMap = mutableMapOf<Int, Int>()
        val options = EventOptions()
        options.callback = { _: BaseEvent, status: Int, _: String ->
            eventCompleteCount++
            statusMap.put(status, statusMap.getOrDefault(status, 0) + 1)
        }
        amplitude.track("test event 1", options = options)
        runRequest()
        amplitude.track("test event 2", options = options)
        runRequest()
        runRequest()
        Thread.sleep(100)
        // verify retry events success
        assertEquals(3, server.requestCount)
        assertEquals(2, statusMap.get(200))
        assertEquals(2, eventCompleteCount)
    }

    @Test
    fun `test handle failed response`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"500\"}").setResponseCode(500))
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
        var eventCompleteCount = 0
        val statusMap = mutableMapOf<Int, Int>()
        val options = EventOptions()
        options.callback = { _: BaseEvent, status: Int, _: String ->
            eventCompleteCount++
            statusMap.put(status, statusMap.getOrDefault(status, 0) + 1)
        }
        amplitude.track("test event 1", options = options)
        runRequest()
        amplitude.track("test event 2", options = options)
        runRequest()
        runRequest()
        Thread.sleep(100)
        // verify retry events success
        assertEquals(3, server.requestCount)
        assertEquals(2, statusMap.get(200))
        assertEquals(2, eventCompleteCount)
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

    companion object {
        private fun setAmplitudeDispatchers(
            amplitude: com.amplitude.core.Amplitude,
            testCoroutineScheduler: TestCoroutineScheduler,
        ) {
            // inject these dispatcher fields with reflection, as the field is val (read-only)
            listOf(
                "amplitudeDispatcher",
                "networkIODispatcher",
                "storageIODispatcher"
            ).forEach { dispatcherField ->
                com.amplitude.core.Amplitude::class.java.getDeclaredField(dispatcherField)
                    .apply {
                        isAccessible = true
                        set(amplitude, StandardTestDispatcher(testCoroutineScheduler))
                    }
            }
        }
    }
}
