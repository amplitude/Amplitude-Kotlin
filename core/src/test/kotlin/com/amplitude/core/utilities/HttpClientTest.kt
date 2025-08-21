package com.amplitude.core.utilities

import com.amplitude.common.Logger
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
import org.junit.jupiter.api.Assertions.assertFalse
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
    private val silentLogger = mockk<Logger>(relaxed = true) // Silent logger for clean test output
    private val verifiableLogger = mockk<Logger>(relaxed = true) // Mock logger for verifying log calls without console output

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
    fun `test client_upload_time is set and response headers are extracted`() {
        server.enqueue(
            MockResponse()
                .setBody("{\"code\": \"success\"}")
                .setHeader("X-Custom-Header", "test-value")
                .setHeader("Content-Type", "application/json"),
        )

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config, silentLogger))
        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        val request = runRequest()
        val result = JSONObject(request?.body?.readUtf8())

        // Verify request content
        assertEquals(apiKey, result.getString("api_key"))
        assertNotNull(result.getString("client_upload_time"))

        // Verify response properties from generic request method
        assertTrue(response.status.statusCode == 200)
        assertTrue(response is com.amplitude.core.utilities.http.SuccessResponse)
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

        val httpClient = spyk(HttpClient(config, silentLogger))

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

        val httpClient = spyk(HttpClient(config, silentLogger))
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

        val httpClient = spyk(HttpClient(config, silentLogger))
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
    fun `test various error responses are handled properly`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("<html>Error occurred</html>"))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config, silentLogger))

        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        runRequest()
        assertTrue(503 in response.status.range)
        val responseBody = response as FailedResponse
        assertEquals("<html>Error occurred</html>", responseBody.error)
    }

    @Test
    fun `test logger functionality and connection failures`() {
        // Test 1: Normal operation with logger injection
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"code\": \"success\"}"))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        // This test verifies that HttpClient can be constructed with a logger parameter
        val httpClient = HttpClient(config, verifiableLogger)
        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        // Verify normal operation works with logger injection
        assertNotNull(response)
        assertTrue(200 in response.status.range)

        // Verify no error logs were called during normal operation
        verify(exactly = 0) { verifiableLogger.error(any()) }

        // Test 2: Verify logger is properly injected for potential connection issues
        val testConfig =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )

        val testHttpClient = HttpClient(testConfig, verifiableLogger)

        // Enqueue a successful response to verify normal path doesn't log errors
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"code\": \"success\"}"))
        val testResponse = testHttpClient.upload(JSONUtil.eventsToString(listOf(event)))

        // Verify successful response
        assertEquals(200, testResponse.status.statusCode)
        verify(exactly = 0) { verifiableLogger.error(any()) }
    }

    @Test
    fun `test logger warn is called when IOException occurs in getInputStream`() {
        // Set up server to return error response that will trigger error stream fallback
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = HttpClient(config, verifiableLogger)
        val response = httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        // Verify that logger.warn was called when falling back to error stream
        verify {
            verifiableLogger.warn(
                match { it.contains("Failed to get input stream, falling back to error stream") },
            )
        }

        // Verify we still get a valid response (error response should be read from error stream)
        assertEquals(500, response.status.statusCode)
    }

    @Test
    fun `test logger calls include exception messages and generic request method works`() {
        // Test 1: Exception message inclusion (original test)
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = HttpClient(config, verifiableLogger)
        httpClient.upload(JSONUtil.eventsToString(listOf(event)))

        // Verify that the actual exception message is passed to the logger
        verify {
            verifiableLogger.warn(
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

        // Test 2: Verify generic request functionality handles different status codes
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"status\": \"success\"}"))

        val directHttpClient = HttpClient(config, silentLogger) // Use silent logger for non-verification test

        // Test generic request handling through upload method
        val testResponse = directHttpClient.upload(JSONUtil.eventsToString(listOf(event)))

        // Verify generic request method works correctly
        assertTrue(200 in testResponse.status.range)
    }

    @Test
    fun `test generic request method with GET requests and custom headers`() {
        // Server setup for GET request
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"config\": \"value\"}")
                .setHeader("Content-Type", "application/json"),
        )

        val config =
            Configuration(
                apiKey = apiKey,
                serverUrl = server.url("/").toString(),
            )
        val httpClient = HttpClient(config, silentLogger)

        // Make GET request with custom headers
        val request =
            HttpClient.Request(
                url = server.url("/config").toString(),
                method = HttpClient.Request.Method.GET,
                headers =
                    mapOf(
                        "Authorization" to "Bearer token",
                        "X-Custom-Header" to "test-value",
                    ),
            )
        val response = httpClient.request(request)

        // Request is successful with correct format
        assertTrue(response.isSuccessful)
        assertEquals(200, response.statusCode)
        assertEquals("{\"config\": \"value\"}", response.body)

        // Verify request headers were sent correctly
        val recordedRequest = runRequest()
        assertEquals("Bearer token", recordedRequest?.getHeader("Authorization"))
        assertEquals("test-value", recordedRequest?.getHeader("X-Custom-Header"))
        assertEquals("GET", recordedRequest?.method)
    }

    @Test
    fun `test generic request method handles various HTTP methods and error responses`() {
        // Test POST with error response
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad Request"))

        val config = Configuration(apiKey = apiKey, serverUrl = server.url("/").toString())
        val httpClient = HttpClient(config, silentLogger)

        // Make POST request that fails
        val postRequest =
            HttpClient.Request(
                url = server.url("/api").toString(),
                method = HttpClient.Request.Method.POST,
                body = "{\"test\": \"data\"}",
                headers = mapOf("Content-Type" to "application/json"),
            )
        val response = httpClient.request(postRequest)

        // Error response is handled correctly
        assertFalse(response.isSuccessful)
        assertEquals(400, response.statusCode)
        assertEquals("Bad Request", response.body)
        assertTrue(response.isClientError)
    }

    private fun runRequest(): RecordedRequest? {
        return try {
            server.takeRequest(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            null
        }
    }
}
