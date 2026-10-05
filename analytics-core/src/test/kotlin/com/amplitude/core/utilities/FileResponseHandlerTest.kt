@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.AnalyticsResponse
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONException
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
    fun `should remove uploaded file before callback without dispatching storage work`() =
        runTest {
            var callbackInvoked = false
            val handler =
                FileResponseHandler(
                    storage = storage,
                    eventPipeline = pipeline,
                    configuration =
                        Configuration(
                            apiKey = "test",
                            callback = { _, _, _ ->
                                verify(exactly = 1) { storage.removeFile("file_path") }
                                callbackInvoked = true
                            },
                        ),
                    scope = this,
                    storageDispatcher = StandardTestDispatcher(testScheduler),
                    logger = null,
                )

            handler.handleSuccessResponse(
                SuccessResponse(),
                "file_path",
                JSONUtil.eventsToString(listOf(generateBaseEvent("test"))),
            )

            assertTrue(callbackInvoked)
            verify(exactly = 1) { storage.removeFile("file_path") }
        }

    @Test
    fun `should remove uploaded file before callback parsing fails`() =
        runTest {
            val handler =
                FileResponseHandler(
                    storage,
                    pipeline,
                    Configuration(apiKey = "test"),
                    this,
                    StandardTestDispatcher(testScheduler),
                    null,
                )

            assertThrows<JSONException> {
                handler.handleSuccessResponse(SuccessResponse(), "file_path", "not json")
            }

            // The parser's asynchronous cleanup has not run yet.
            verify(exactly = 1) { storage.removeFile("file_path") }
        }

    @Test
    fun `should continue callbacks and remove registrations when callbacks throw`() =
        runTest {
            val logger = mockk<Logger>(relaxed = true)
            val globalCallbacks = mutableListOf<String>()
            val eventCallbacks = mutableListOf<String>()
            val events = listOf(generateBaseEvent("first"), generateBaseEvent("second"))
            events.forEach { event ->
                event.insertId = event.eventType
                every { storage.getEventCallback(event.eventType) } returns { received, _, _ ->
                    eventCallbacks.add(received.eventType)
                    if (received.eventType == "first") throw IllegalStateException("Event callback failed")
                }
                every { storage.removeEventCallback(event.eventType) } returns Unit
            }
            val handler =
                FileResponseHandler(
                    storage = storage,
                    eventPipeline = pipeline,
                    configuration =
                        Configuration(
                            apiKey = "test",
                            callback = { event, _, _ ->
                                globalCallbacks.add(event.eventType)
                                if (event.eventType == "first") throw IllegalStateException("Global callback failed")
                            },
                        ),
                    scope = this,
                    storageDispatcher = StandardTestDispatcher(testScheduler),
                    logger = logger,
                )

            handler.handleSuccessResponse(SuccessResponse(), "file_path", JSONUtil.eventsToString(events))
            runCurrent()

            assertEquals(listOf("first", "second"), globalCallbacks)
            assertEquals(listOf("first", "second"), eventCallbacks)
            verify(exactly = 1) { storage.removeFile("file_path") }
            verify(exactly = 1) { storage.removeEventCallback("first") }
            verify(exactly = 1) { storage.removeEventCallback("second") }
            verify { logger.error(match { it.contains("Global callback failed") }) }
            verify { logger.error(match { it.contains("Event callback failed") }) }
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
    fun `bad request with plain text proxy body releases file for retry`() {
        val response = AnalyticsResponse.create(400, "invalid_api_key") as BadRequestResponse
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

        verify(exactly = 1) {
            storage.releaseFile("file_path")
        }
        verify(exactly = 0) {
            storage.removeFile("file_path")
        }
        assertTrue(shouldRetryUploadOnFailure)
    }

    @Test
    fun `payload too large with plain text body splits the file`() {
        val response = AnalyticsResponse.create(413, "Request too large.") as PayloadTooLargeResponse
        handler.handlePayloadTooLargeResponse(
            payloadTooLargeResponse = response,
            events = "file_path",
            eventsString =
                JSONUtil.eventsToString(
                    listOf(
                        generateBaseEvent("test1"),
                        generateBaseEvent("test2"),
                    ),
                ),
        )

        verify(exactly = 1) {
            storage.splitEventFile("file_path", any())
        }
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
