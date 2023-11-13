package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.events.BaseEvent
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpClientTest {
    private lateinit var server: MockWebServer
    val apiKey = "API_KEY"
    val clientUploadTimeMillis = 1699905773000L
    val clientUploadTimeString = "2023-11-13T20:02:53.000Z"

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

        val config = Configuration(
            apiKey = apiKey,
            serverUrl = server.url("/").toString()
        )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config))
        every { httpClient.getCurrentTimeMillis() } returns clientUploadTimeMillis

        val connection = httpClient.upload()
        System.currentTimeMillis()

        connection.outputStream?.let {
            connection.setEvents(JSONUtil.eventsToString(listOf(event)))
            // Upload the payloads.
            connection.close()
        }

        val request: RecordedRequest? = runRequest()
        val body = request?.body?.readUtf8()
        val result = JSONObject(body)

        assertEquals(apiKey, result.getString("api_key"))
        assertEquals(clientUploadTimeString, result.getString("client_upload_time"))
    }

    @Test
    fun `test client_upload_time is set correctly when minIdLength is set`() {
        server.enqueue(MockResponse().setBody("{\"code\": \"success\"}"))

        val config = Configuration(
            apiKey = apiKey,
            serverUrl = server.url("/").toString(),
            minIdLength = 3,
        )
        val event = BaseEvent()
        event.eventType = "test"

        val httpClient = spyk(HttpClient(config))
        every { httpClient.getCurrentTimeMillis() } returns clientUploadTimeMillis

        val connection = httpClient.upload()
        System.currentTimeMillis()

        connection.outputStream?.let {
            connection.setEvents(JSONUtil.eventsToString(listOf(event)))
            // Upload the payloads.
            connection.close()
        }

        val request: RecordedRequest? = runRequest()
        val body = request?.body?.readUtf8()
        val result = JSONObject(body)

        assertEquals(apiKey, result.getString("api_key"))
        assertEquals(clientUploadTimeString, result.getString("client_upload_time"))
    }

    private fun runRequest(): RecordedRequest? {
        return try {
            server.takeRequest(5, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            null
        }
    }
}
