package com.amplitude.core.utilities.http

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnalyticsRequestTest {
    @Test
    fun `test with api key and events`() {
        val analyticsRequest = AnalyticsRequest(
            "API_KEY",
            "{\"event_type\":\"test\"}",
            clientUploadTime = 1629849600000
        )
        val bodyStr = analyticsRequest.getBodyStr()
        assertEquals(
            "{\"api_key\":\"API_KEY\",\"client_upload_time\":\"2021-08-25T00:00:00.000Z\",\"events\":{\"event_type\":\"test\"}}",
            bodyStr
        )
    }

    @Test
    fun `test with min id length`() {
        val analyticsRequest = AnalyticsRequest(
            "API_KEY",
            "{\"event_type\":\"test\"}",
            minIdLength = 10,
            clientUploadTime = 1629849600000
        )
        val bodyStr = analyticsRequest.getBodyStr()
        assertEquals(
            "{\"api_key\":\"API_KEY\",\"client_upload_time\":\"2021-08-25T00:00:00.000Z\",\"events\":{\"event_type\":\"test\"},\"options\":{\"min_id_length\":10}}",
            bodyStr
        )
    }

    @Test
    fun `test with diagnostics`() {
        val analyticsRequest = AnalyticsRequest(
            "API_KEY",
            "{\"event_type\":\"test\"}",
            diagnostics = "{\"sdk\":\"android\"}",
            clientUploadTime = 1629849600000
        )
        val bodyStr = analyticsRequest.getBodyStr()
        assertEquals(
            "{\"api_key\":\"API_KEY\",\"client_upload_time\":\"2021-08-25T00:00:00.000Z\",\"events\":{\"event_type\":\"test\"},\"request_metadata\":{\"sdk\":{\"sdk\":\"android\"}}}",
            bodyStr
        )
    }
}