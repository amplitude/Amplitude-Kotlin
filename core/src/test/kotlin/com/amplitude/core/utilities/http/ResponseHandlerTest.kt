package com.amplitude.core.utilities.http

import com.amplitude.core.utilities.http.HttpStatus.BAD_REQUEST
import com.amplitude.core.utilities.http.HttpStatus.FAILED
import com.amplitude.core.utilities.http.HttpStatus.PAYLOAD_TOO_LARGE
import com.amplitude.core.utilities.http.HttpStatus.SUCCESS
import com.amplitude.core.utilities.http.HttpStatus.TIMEOUT
import com.amplitude.core.utilities.http.HttpStatus.TOO_MANY_REQUESTS
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponseHandlerTest {

    private val fakeResponseHandler = object : ResponseHandler {
        var badRequestResult = false

        override fun handleSuccessResponse(
            successResponse: SuccessResponse,
            events: Any,
            eventsString: String,
        ) {
        }

        override fun handleBadRequestResponse(
            badRequestResponse: BadRequestResponse,
            events: Any,
            eventsString: String,
        ): Boolean = badRequestResult

        override fun handlePayloadTooLargeResponse(
            payloadTooLargeResponse: PayloadTooLargeResponse,
            events: Any,
            eventsString: String,
        ) {
        }

        override fun handleTooManyRequestsResponse(
            tooManyRequestsResponse: TooManyRequestsResponse,
            events: Any,
            eventsString: String,
        ) {
        }

        override fun handleTimeoutResponse(
            timeoutResponse: TimeoutResponse,
            events: Any,
            eventsString: String,
        ) {
        }

        override fun handleFailedResponse(
            failedResponse: FailedResponse,
            events: Any,
            eventsString: String,
        ) {
        }
    }

    @Test
    fun `default shouldRetryUploadOnFailure on the interface`() {
        val statuses = HttpStatus.values()
        val expected = mapOf(
            SUCCESS to null,
            BAD_REQUEST to false,
            TIMEOUT to true,
            PAYLOAD_TOO_LARGE to true,
            TOO_MANY_REQUESTS to true,
            FAILED to true
        )
        statuses.forEach { status ->
            val analyticsResponse = AnalyticsResponse.create(status.code, responseBody = "{}")
            val shouldRetryUploadOnFailure = fakeResponseHandler.handle(
                response = analyticsResponse,
                events = emptyList<AnalyticsResponse>(),
                eventsString = "",
            )
            assertTrue(
                expected[status] == shouldRetryUploadOnFailure,
                "Expected $status to be ${expected[status]}, but got $shouldRetryUploadOnFailure"
            )
        }
    }

    @Test
    fun `shouldRetryUploadOnFailure - handleBadRequestResponse`() {
        val badRequestResponse = AnalyticsResponse.create(BAD_REQUEST.code, responseBody = "{}")

        fakeResponseHandler.badRequestResult = false
        var shouldRetryUploadOnFailure = fakeResponseHandler.handle(
            response = badRequestResponse,
            events = emptyList<AnalyticsResponse>(),
            eventsString = "",
        )
        assertFalse(shouldRetryUploadOnFailure!!)

        fakeResponseHandler.badRequestResult = true
        shouldRetryUploadOnFailure = fakeResponseHandler.handle(
            response = badRequestResponse,
            events = emptyList<AnalyticsResponse>(),
            eventsString = "",
        )
        assertTrue(shouldRetryUploadOnFailure!!)
    }
}
