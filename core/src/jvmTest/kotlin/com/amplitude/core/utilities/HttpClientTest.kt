package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.http.FailedResponse
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpClientTest {
    private lateinit var server: MockWebServer
    val apiKey = "API_KEY"

    @ExperimentalCoroutinesApi
    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun shutdown() {
        server.shutdown()
    }

    @Test
    fun `test client_upload_time is set on the request`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config))
        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        val request = runRequest()
        val result = JSONObject(request?.body?.readUtf8())

        assertEquals(apiKey, result.getString("api_key"))
        assertNotNull(result.getString("client_upload_time"))
    }

    @Test
    fun `test client_upload_time is set correctly when minIdLength is set`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
                minIdLength = 3,
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config))

        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        val request = runRequest()
        val result = JSONObject(request?.body?.readUtf8())

        assertEquals(apiKey, result.getString("api_key"))
        assertNotNull(result.getString("client_upload_time"))
        assertNull(result.optJSONObject("request_metadata"))
    }

    @Test
    fun `test payload is correct when diagnostics are set`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config))
        val diagnostics = Diagnostics()
        diagnostics.addErrorLog("error")
        diagnostics.addMalformedEvent("malformed-event")

        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)), diagnostics.extractDiagnostics())

        val request = runRequest()
        val result = JSONObject(request?.body?.readUtf8())

        assertEquals(apiKey, result.getString("api_key"))
        assertEquals(
            "{\"error_logs\":[\"error\"],\"malformed_events\":[\"malformed-event\"]}",
            result.getJSONObject("request_metadata").getJSONObject("sdk").toString(),
        )
    }

    @Test
    fun `test correct response when null or empty payload`() {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config))
        val diagnostics = Diagnostics()
        diagnostics.addErrorLog("error")
        diagnostics.addMalformedEvent("malformed-event")

        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)), diagnostics.extractDiagnostics())

        runRequest()
        assertEquals(200, response.status.code)

        runRequest()
        assertEquals(200, response.status.code)
    }

    @Test
    fun `test html error response is handled properly`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("<html>Error occurred</html>"))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config))

        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        runRequest()
        // Error code 503 is converted to a 500 in the http client
        assertEquals(500, response.status.code)
        val responseBody = response as FailedResponse
        assertEquals("<html>Error occurred</html>", responseBody.error)
    }

    private fun runRequest(): RecordedRequest? {
        return try {
            server.takeRequest(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            null
        }
    }
}
