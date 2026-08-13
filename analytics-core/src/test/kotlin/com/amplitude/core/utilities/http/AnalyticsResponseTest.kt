package com.amplitude.core.utilities.http

import com.amplitude.core.events.BaseEvent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AnalyticsResponseTest {
    @Test
    fun `test create success response`() {
        listOf(200, 202, 299).forEach {
            val response = AnalyticsResponse.create(it, null)
            assertTrue(response is SuccessResponse)
            assertEquals(HttpStatus.SUCCESS, response.status)
        }
    }

    @Test
    fun `test create bad request response`() {
        val responseBody =
            JSONObject().apply {
                put("error", "Invalid API key")
                put("events_with_invalid_fields", JSONObject().put("time", JSONArray().put(0)))
            }.toString()

        val response = AnalyticsResponse.create(400, responseBody)
        assertTrue(response is BadRequestResponse)
        response as BadRequestResponse
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals("Invalid API key", response.error)
        assertEquals(setOf(0), response.getEventIndicesToDrop())
        assertTrue(response.isInvalidApiKeyResponse())
    }

    @Test
    fun `test create payload too large response`() {
        val responseBody =
            JSONObject().apply {
                put("error", "Payload too large")
            }.toString()

        val response = AnalyticsResponse.create(413, responseBody)
        assertTrue(response is PayloadTooLargeResponse)
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.status)
        assertEquals("Payload too large", (response as PayloadTooLargeResponse).error)
    }

    @Test
    fun `test create too many requests response parses quota and throttle fields`() {
        val responseBody =
            JSONObject().apply {
                put("error", "Too many requests")
                put("exceeded_daily_quota_users", JSONObject().put("user-1", 1))
                put("exceeded_daily_quota_devices", JSONObject().put("device-1", 1))
                put("throttled_users", JSONObject().put("user-2", 10))
                put("throttled_devices", JSONObject().put("device-2", 10))
                put("throttled_events", JSONArray().put(0).put(2))
            }.toString()

        val response = AnalyticsResponse.create(429, responseBody)
        assertTrue(response is TooManyRequestsResponse)
        response as TooManyRequestsResponse
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.status)
        assertEquals("Too many requests", response.error)
        assertEquals(setOf(0, 2), response.throttledEvents)
        assertTrue(response.isEventExceedDailyQuota(BaseEvent().apply { userId = "user-1" }))
        assertTrue(response.isEventExceedDailyQuota(BaseEvent().apply { deviceId = "device-1" }))
        assertFalse(response.isEventExceedDailyQuota(BaseEvent().apply { userId = "user-2" }))
    }

    @Test
    fun `test create timeout response`() {
        val response = AnalyticsResponse.create(408, null)
        assertTrue(response is TimeoutResponse)
        assertEquals(HttpStatus.TIMEOUT, response.status)
    }

    @Test
    fun `test create failed response`() {
        val responseBody =
            JSONObject().apply {
                put("error", "Internal server error")
            }.toString()

        val response = AnalyticsResponse.create(500, responseBody)
        assertTrue(response is FailedResponse)
        assertEquals(HttpStatus.FAILED, response.status)
        assertEquals("Internal server error", (response as FailedResponse).error)
    }

    @Test
    fun `test create failed response with invalid JSON`() {
        listOf(500, 503, 599).forEach {
            val response = AnalyticsResponse.create(it, "Invalid JSON")
            assertTrue(response is FailedResponse)
            assertEquals(HttpStatus.FAILED, response.status)
            assertEquals("Invalid JSON", (response as FailedResponse).error)
        }
    }

    @Test
    fun `test create failed response with null body`() {
        val response = AnalyticsResponse.create(500, null)
        assertTrue(response is FailedResponse)
        assertEquals(HttpStatus.FAILED, response.status)
        assertEquals("", (response as FailedResponse).error)
    }

    @Nested
    inner class `non-json 4xx bodies` {
        @Test
        fun `should parse plain text 400 as bad request without throwing`() {
            val response = AnalyticsResponse.create(400, "invalid_api_key")
            assertTrue(response is BadRequestResponse)
            response as BadRequestResponse
            assertEquals(HttpStatus.BAD_REQUEST, response.status)
            assertEquals("invalid_api_key", response.error)
            assertTrue(response.getEventIndicesToDrop().isEmpty())
            assertFalse(response.isInvalidApiKeyResponse())
        }

        @Test
        fun `should parse plain text 413 as payload too large without throwing`() {
            val response = AnalyticsResponse.create(413, "Request too large.")
            assertTrue(response is PayloadTooLargeResponse)
            assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.status)
            assertEquals("Request too large.", (response as PayloadTooLargeResponse).error)
        }

        @Test
        fun `should parse plain text 429 as too many requests without throwing`() {
            val response = AnalyticsResponse.create(429, "Too many requests")
            assertTrue(response is TooManyRequestsResponse)
            response as TooManyRequestsResponse
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.status)
            assertEquals("Too many requests", response.error)
            assertTrue(response.throttledEvents.isEmpty())
        }

        @Test
        fun `should parse null 4xx bodies without throwing`() {
            listOf(400, 413, 429).forEach { statusCode ->
                val response = AnalyticsResponse.create(statusCode, null)
                assertEquals(
                    when (statusCode) {
                        400 -> HttpStatus.BAD_REQUEST
                        413 -> HttpStatus.PAYLOAD_TOO_LARGE
                        else -> HttpStatus.TOO_MANY_REQUESTS
                    },
                    response.status,
                )
            }
        }
    }
}
