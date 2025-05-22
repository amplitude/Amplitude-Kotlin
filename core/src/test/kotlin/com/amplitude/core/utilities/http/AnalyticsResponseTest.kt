package com.amplitude.core.utilities.http

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `test create payload too large response`() {
        val responseBody = JSONObject().apply {
            put("error", "Payload too large")
        }.toString()

        val response = AnalyticsResponse.create(413, responseBody)
        assertTrue(response is PayloadTooLargeResponse)
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.status)
        assertEquals("Payload too large", (response as PayloadTooLargeResponse).error)
    }

    @Test
    fun `test create timeout response`() {
        val response = AnalyticsResponse.create(408, null)
        assertTrue(response is TimeoutResponse)
        assertEquals(HttpStatus.TIMEOUT, response.status)
    }

    @Test
    fun `test create failed response`() {
        val responseBody = JSONObject().apply {
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
}
