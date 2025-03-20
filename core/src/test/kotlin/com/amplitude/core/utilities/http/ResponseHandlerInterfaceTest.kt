package com.amplitude.core.utilities.http

import com.amplitude.core.utilities.http.HttpStatus.BAD_REQUEST
import com.amplitude.core.utilities.http.HttpStatus.FAILED
import com.amplitude.core.utilities.http.HttpStatus.PAYLOAD_TOO_LARGE
import com.amplitude.core.utilities.http.HttpStatus.SUCCESS
import com.amplitude.core.utilities.http.HttpStatus.TIMEOUT
import com.amplitude.core.utilities.http.HttpStatus.TOO_MANY_REQUESTS
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponseHandlerTest {

    @Test
    fun `test shouldRetryUploadOnFailure`() {
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
            val shouldRetryUploadOnFailure = when (status) {
                SUCCESS -> null
                BAD_REQUEST -> false
                TIMEOUT,
                PAYLOAD_TOO_LARGE,
                TOO_MANY_REQUESTS,
                FAILED,
                    -> true
            }
            assertTrue(
                expected[status] == shouldRetryUploadOnFailure,
                "Expected $status to be ${expected[status]}, but got $shouldRetryUploadOnFailure"
            )
        }
    }
}