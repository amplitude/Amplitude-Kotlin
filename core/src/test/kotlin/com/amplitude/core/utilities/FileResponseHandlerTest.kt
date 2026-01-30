package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.BadRequestResponse
import com.amplitude.core.utilities.http.FailedResponse
import com.amplitude.core.utilities.http.PayloadTooLargeResponse
import com.amplitude.core.utilities.http.SuccessResponse
import com.amplitude.core.utilities.http.TimeoutResponse
import com.amplitude.core.utilities.http.TooManyRequestsResponse
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileResponseHandlerTest {
    private val storage = mockk<EventsFileStorage>()
    private val pipeline = mockk<EventPipeline>(relaxed = true)
    private val handler =
        FileResponseHandler(
            storage = storage,
            eventPipeline = pipeline,
            configuration =
                Configuration(
                    apiKey = "test",
                    callback = { event: BaseEvent, _: Int, _: String ->
                        configCallBackEventTypes.add(event.eventType)
                    },
                ),
            diagnosticsClient = mockk<DiagnosticsClient>(relaxed = true),
            scope = TestScope(),
            storageDispatcher = UnconfinedTestDispatcher(),
            logger = null,
        )
    private var configCallBackEventTypes = mutableListOf<String>()

    init {
        every {
            storage.removeFile("file_path")
        } returns true
        every {
            storage.splitEventFile("file_path", any())
        } returns Unit
        every {
            storage.releaseFile("file_path")
        } returns Unit
    }

    @Test
    fun `success single event`() {
        val response = SuccessResponse()

        val events =
            listOf(
                generateBaseEvent("test1"),
            )
        handler.handleSuccessResponse(
            successResponse = response,
            events = "file_path",
            eventsString = JSONUtil.eventsToString(events),
        )

        assertTrue(configCallBackEventTypes.contains("test1"))
        verify(exactly = 1) {
            storage.removeFile("file_path")
        }
    }

    @Test
    fun `success multiple events`() {
        val response = SuccessResponse()

        val events =
            listOf(
                generateBaseEvent("test1"),
                generateBaseEvent("test2"),
                generateBaseEvent("test3"),
            )
        handler.handleSuccessResponse(
            successResponse = response,
            events = "file_path",
            eventsString = JSONUtil.eventsToString(events),
        )

        val expectedEventTypes = events.map { it.eventType }
        assertTrue(configCallBackEventTypes.containsAll(expectedEventTypes))
        verify(exactly = 1) {
            storage.removeFile("file_path")
        }
    }

    @Test
    fun `bad request for invalid API key`() {
        val response =
            BadRequestResponse(
                JSONObject("{\"error\":\"Invalid API key\"}"),
            )

        val shouldRetryUploadOnFailure =
            handler.handleBadRequestResponse(
                badRequestResponse = response,
                events = "file_path",
                eventsString =
                    JSONUtil.eventsToString(
                        listOf(
                            generateBaseEvent("test1"),
                            generateBaseEvent("test2"),
                        ),
                    ),
            )

        assertTrue(configCallBackEventTypes.contains("test1"))
        verify(exactly = 1) {
            storage.removeFile("file_path")
        }
        assertFalse(shouldRetryUploadOnFailure)
    }

    @Test
    fun `bad request multiple events`() {
        val response =
            BadRequestResponse(
                JSONObject("{\"error\":\"Some Error\"}"),
            )

        val events =
            listOf(
                generateBaseEvent("test1"),
                generateBaseEvent("test2"),
                generateBaseEvent("test3"),
            )
        val shouldRetryUploadOnFailure =
            handler.handleBadRequestResponse(
                badRequestResponse = response,
                events = "file_path",
                eventsString = JSONUtil.eventsToString(events),
            )

        verify(exactly = 1) {
            storage.releaseFile("file_path")
        }
        assertTrue(shouldRetryUploadOnFailure)
        verify(exactly = 0) {
            pipeline.put(any())
        }
        verify(exactly = 0) {
            storage.removeFile("file_path")
        }
    }

    @Test
    fun `bad request multiple events with events_with_invalid_fields and retry`() {
        val badRequestResponseBody =
            """
            {
              "code": 400,
              "error": "Request missing required field",
              "events_with_invalid_fields": {
                "time": [
                  0
                ]
              }
            }
            """.trimIndent()
        val response =
            BadRequestResponse(
                JSONObject(badRequestResponseBody),
            )

        val events =
            listOf(
                generateBaseEvent("test1"),
                generateBaseEvent("test2"),
                generateBaseEvent("test3"),
            )
        val shouldRetryUploadOnFailure =
            handler.handleBadRequestResponse(
                badRequestResponse = response,
                events = "file_path",
                eventsString = JSONUtil.eventsToString(events),
            )

        assertTrue(configCallBackEventTypes.contains("test1"))
        verify {
            pipeline.put(match { it.eventType == "test2" })
        }
        verify {
            pipeline.put(match { it.eventType == "test3" })
        }
        verify {
            storage.removeFile("file_path")
        }
        assertFalse(shouldRetryUploadOnFailure)
    }

    @Test
    fun `bad request multiple events with silenced_events and retry`() {
        val badRequestResponseBody =
            """
            {
              "code": 400,
              "error": "Request missing required field",
              "silenced_events": [
                0
              ]
            }
            """.trimIndent()
        val response =
            BadRequestResponse(
                JSONObject(badRequestResponseBody),
            )

        val events =
            listOf(
                generateBaseEvent("test1"),
                generateBaseEvent("test2"),
                generateBaseEvent("test3"),
            )
        val shouldRetryUploadOnFailure =
            handler.handleBadRequestResponse(
                badRequestResponse = response,
                events = "file_path",
                eventsString = JSONUtil.eventsToString(events),
            )

        assertTrue(configCallBackEventTypes.contains("test1"))
        verify {
            pipeline.put(match { it.eventType == "test2" })
        }
        verify {
            pipeline.put(match { it.eventType == "test3" })
        }
        verify(exactly = 1) {
            storage.removeFile("file_path")
        }
        assertFalse(shouldRetryUploadOnFailure)
    }

    @Test
    fun `handle payload too large with single event`() {
        val response =
            PayloadTooLargeResponse(
                JSONObject("{\"error\":\"Payload too large\"}"),
            )

        val events =
            listOf(
                generateBaseEvent("test1"),
            )
        handler.handlePayloadTooLargeResponse(
            payloadTooLargeResponse = response,
            events = "file_path",
            eventsString = JSONUtil.eventsToString(events),
        )

        assertTrue(configCallBackEventTypes.contains("test1"))
        verify(exactly = 1) {
            storage.removeFile("file_path")
        }
    }

    @Test
    fun `handle payload too large with multiple events`() {
        val response =
            PayloadTooLargeResponse(
                JSONObject("{\"error\":\"Payload too large\"}"),
            )

        val events =
            listOf(
                generateBaseEvent("test1"),
                generateBaseEvent("test2"),
                generateBaseEvent("test3"),
            )
        handler.handlePayloadTooLargeResponse(
            payloadTooLargeResponse = response,
            events = "file_path",
            eventsString = JSONUtil.eventsToString(events),
        )

        verify(exactly = 1) {
            storage.splitEventFile("file_path", any())
        }
    }

    @Test
    fun `handle too many requests with multiple events`() {
        val response =
            TooManyRequestsResponse(
                JSONObject("{\"error\":\"Too many requests\"}"),
            )

        val events =
            listOf(
                generateBaseEvent("test1"),
                generateBaseEvent("test2"),
                generateBaseEvent("test3"),
            )
        handler.handleTooManyRequestsResponse(
            tooManyRequestsResponse = response,
            events = "file_path",
            eventsString = JSONUtil.eventsToString(events),
        )

        verify(exactly = 1) {
            storage.releaseFile("file_path")
        }
    }

    @Test
    fun `handle timeout with multiple events`() {
        val response = TimeoutResponse()

        val events =
            listOf(
                generateBaseEvent("test1"),
                generateBaseEvent("test2"),
                generateBaseEvent("test3"),
            )
        handler.handleTimeoutResponse(
            timeoutResponse = response,
            events = "file_path",
            eventsString = JSONUtil.eventsToString(events),
        )

        verify(exactly = 1) {
            storage.releaseFile("file_path")
        }
    }

    @Test
    fun `handle failed response with multiple events`() {
        val response =
            FailedResponse(
                JSONObject("{\"error\":\"Request failed\"}"),
            )

        val events =
            listOf(
                generateBaseEvent("test1"),
                generateBaseEvent("test2"),
                generateBaseEvent("test3"),
            )
        handler.handleFailedResponse(
            failedResponse = response,
            events = "file_path",
            eventsString = JSONUtil.eventsToString(events),
        )

        verify(exactly = 1) {
            storage.releaseFile("file_path")
        }
    }

    private fun generateBaseEvent(eventType: String) =
        BaseEvent()
            .apply {
                this.eventType = eventType
            }
}
