package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.http.FailedResponse
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpClientTest {
    private lateinit var server: MockWebServer
    private val apiKey = "API_KEY"
    private val logger = ConsoleLogger()

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

        val httpClient = spyk(HttpClient(config, logger))
        httpClient.upload(JSONUtil.eventsToString(listOf(event)))

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

        val httpClient = spyk(HttpClient(config, logger))

        httpClient.upload(JSONUtil.eventsToString(listOf(event)))

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

        val httpClient = spyk(HttpClient(config, logger))
        val diagnostics = Diagnostics()
        diagnostics.addErrorLog("error")
        diagnostics.addMalformedEvent("malformed-event")

        httpClient.upload(JSONUtil.eventsToString(listOf(event)), diagnostics.extractDiagnostics())

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

        val httpClient = spyk(HttpClient(config, logger))
        val diagnostics = Diagnostics()
        diagnostics.addErrorLog("error")
        diagnostics.addMalformedEvent("malformed-event")

        val response =
            httpClient.upload(
                JSONUtil.eventsToString(listOf(event)),
                diagnostics.extractDiagnostics(),
            )

        runRequest()
        assertTrue(200 in response.status.range)

        runRequest()
        assertTrue(200 in response.status.range)
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

        val httpClient = spyk(HttpClient(config, logger))

        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        runRequest()
        assertTrue(503 in response.status.range)
        val responseBody = response as FailedResponse
        assertEquals("<html>Error occurred</html>", responseBody.error)
    }

    @Test
    fun `test logger can be injected into HttpClient`() {
        // Simple test that verifies HttpClient accepts logger parameter and functions normally
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"code\": \"success\"}"))

        val mockLogger = mockk<Logger>(relaxed = true)
        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        // This test verifies that HttpClient can be constructed with a logger parameter
        val httpClient = HttpClient(config, mockLogger)
        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        // Verify normal operation works with logger injection
        assertNotNull(response)
        assertTrue(200 in response.status.range)

        // Verify no error logs were called during normal operation
        verify(exactly = 0) { mockLogger.error(any()) }
    }

    @Test
    fun `test logger warn is called when IOException occurs in getInputStream`() {
        // Set up server to return error response that will trigger error stream fallback
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val mockLogger = mockk<Logger>(relaxed = true)
        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = HttpClient(config, mockLogger)
        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        // Verify that logger.warn was called when falling back to error stream
        verify {
            mockLogger.warn(
                match { it.contains("Failed to get input stream, falling back to error stream") },
            )
        }

        // Verify we still get a valid response (error response should be read from error stream)
        assertEquals(500, response.status.statusCode)
    }

    @Test
    fun `test logger calls include exception messages`() {
        // Test that the actual exception message is included in the warn log (using known working scenario)
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val mockLogger = mockk<Logger>(relaxed = true)
        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = HttpClient(config, mockLogger)
        httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        // Verify that the actual exception message is passed to the logger
        verify {
            mockLogger.warn(
                match { message ->
                    message.contains(
                        "Failed to get input stream, falling back to error stream",
                    ) &&
                        message.contains(
                            ":",
                        ) // Should contain the actual exception message after the colon
                },
            )
        }
    }

    private fun runRequest(): RecordedRequest? {
        return try {
            server.takeRequest(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            null
        }
    }
}
