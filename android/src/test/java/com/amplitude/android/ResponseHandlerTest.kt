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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@Ignore
@RunWith(RobolectricTestRunner::class)
class ResponseHandlerTest {
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
        amplitude = Amplitude(
            Configuration(
                apiKey = apiKey,
                context = context,
                serverUrl = server.url("/").toString(),
                autocapture = setOf(),
                flushIntervalMillis = 150,
                identifyBatchIntervalMillis = 1000,
                flushMaxRetries = 3,
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
    fun `test handle on rate limit`() {
        val rateLimitBody = """
        {
          "code": 429,
          "error": "Too many requests for some devices and users",
          "eps_threshold": 30
        }
        """.trimIndent()
        for (i in 1..6) {
            server.enqueue(MockResponse().setBody(rateLimitBody).setResponseCode(429))
        }
        for (k in 1..4) {
            amplitude.track("test event $k")
            runRequest()
        }
        Thread.sleep(100)
        // verify the total request count when reaching max retries
        assertEquals(6, server.requestCount)
    }

    @Test
    fun `test handle payload too large with only one event`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"413\", \"error\": \"payload too large\"}").setResponseCode(413))
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
    fun `test handle payload too large`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"413\", \"error\": \"payload too large\"}").setResponseCode(413))
        server.enqueue(MockResponse().setBody("{\"code\": \"200\"}").setResponseCode(200))
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
        amplitude.track("test event 2", options = options)
        amplitude.track("test event 3", options = options)
        amplitude.track("test event 4", options = options)
        val request = runRequest()
        // verify the first request hit 413
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(4, events.size)
        amplitude.track("test event 5", options = options)
        runRequest()
        runRequest()
        runRequest()
        Thread.sleep(150)
        // verify we completed processing for the events after file split
        assertEquals(4, server.requestCount)
        assertEquals(5, statusMap.get(200))
        assertEquals(5, eventCompleteCount)
    }

    @Test
    fun `test handle bad request response`() {
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
              2
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
            statusMap.put(status, statusMap.getOrDefault(status, 0) + 1)
        }
        amplitude.track("test event 1", options = options)
        amplitude.track("test event 2", options = options)
        amplitude.track("test event 3", options = options)
        amplitude.track("test event 4", options = options)
        // verify first request take 4 events hit 400
        val request = runRequest()
        assertNotNull(request)
        val events = getEventsFromRequest(request!!)
        assertEquals(4, events.size)
        // verify second request take 2 events after removing 2 bad events
        val request2 = runRequest()
        assertNotNull(request2)
        val events2 = getEventsFromRequest(request2!!)
        assertEquals(2, events2.size)
        assertEquals(2, server.requestCount)
        Thread.sleep(10)
        // verify the processed status
        assertEquals(2, statusMap.get(400))
        assertEquals(2, statusMap.get(200))
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
