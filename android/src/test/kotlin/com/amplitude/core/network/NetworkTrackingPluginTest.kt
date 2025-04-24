package com.amplitude.core.network

import com.amplitude.core.Amplitude
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_COMPLETION_TIME
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_DURATION
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_ERROR_MESSAGE
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY_SIZE
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_REQUEST_METHOD
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY_SIZE
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_START_TIME
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_STATUS_CODE
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_URL
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_URL_FRAGMENT
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_URL_QUERY
import com.amplitude.core.Constants.EventTypes.NETWORK_TRACKING
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.network.NetworkTrackingPlugin.CaptureRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import okhttp3.Interceptor.Chain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol.HTTP_1_1
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.IOException
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

class NetworkTrackingPluginTest {
    private val mockAmplitude = mockk<Amplitude>(relaxed = true)

    private fun networkTrackingPlugin(
        overrideCaptureRules: List<CaptureRule>? = null,
        hosts: List<String> = listOf("example.com"),
        statusCodeRange: List<Int> = emptyList(),
        ignoreHosts: List<String> = emptyList(),
        ignoreAmplitudeRequests: Boolean = true,
    ) = NetworkTrackingPlugin(
        captureRules = overrideCaptureRules ?: listOf(
            CaptureRule(
                hosts = hosts,
                statusCodeRange = statusCodeRange
            )
        ),
        ignoreHosts = ignoreHosts,
        ignoreAmplitudeRequests = ignoreAmplitudeRequests
    ).apply { amplitude = mockAmplitude }

    @Test
    fun `capture rules must must be non-empty`() {
        val exception = assertThrows<IllegalArgumentException> {
            networkTrackingPlugin(
                overrideCaptureRules = emptyList(),
                ignoreAmplitudeRequests = false
            )
        }
        assertEquals("Capture rules must not be empty.", exception.message)
    }

    @Test
    fun `capture rules must have non-empty host list`() {
        val exception = assertThrows<IllegalArgumentException> {
            networkTrackingPlugin(
                hosts = emptyList(),
                statusCodeRange = (500..599).toList(),
                ignoreAmplitudeRequests = false
            )
        }
        assertEquals("Capture rules must have a non-empty host list.", exception.message)
    }

    @Test
    fun `capture rules must have non-empty status code range`() {
        val exception = assertThrows<IllegalArgumentException> {
            networkTrackingPlugin(
                statusCodeRange = emptyList(),
                ignoreAmplitudeRequests = false
            )
        }
        assertEquals("Capture rules must have a non-empty status code range.", exception.message)
    }

    @Test
    fun `successful response capture`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(mockInterceptorChain(200))

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
        }
    }

    @Test
    fun `successful response outside capture range - does not trigger trackEvent`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(mockInterceptorChain(404))

        verify(exactly = 0) {
            mockAmplitude.track(any<BaseEvent>(), any())
        }
    }

    @Test
    fun `failed response capture`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = listOf(500),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(mockInterceptorChain(statusCode = 500))

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertEquals(500, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
        }
    }

    @Test
    fun `failed response outside capture range - does not trigger trackEvent`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (400..499).toList(),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(mockInterceptorChain(500))

        verify(exactly = 0) {
            mockAmplitude.track(any<BaseEvent>(), any())
        }
    }

    @Test
    fun `local exception capture`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (0..0) + (500..599),
            ignoreAmplitudeRequests = false
        )

        val exception = IOException("Network error")
        try {
            plugin.intercept(mockInterceptorChain(statusCode = 200, exception = exception))
        } catch (e: IOException) {
            // Expected exception
            assertEquals(e, exception)
        }

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertEquals("Network error", eventProperties[NETWORK_TRACKING_ERROR_MESSAGE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
        }
    }

    @Test
    fun `request ignored by host`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreHosts = listOf("example.com"),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(mockInterceptorChain(statusCode = 200))

        verify(exactly = 0) {
            mockAmplitude.track(any<BaseEvent>(), any())
        }
    }

    @Test
    fun `amplitude request ignored`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = true
        )

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.amplitude.com/test",
            )
        )

        verify(exactly = 0) {
            mockAmplitude.track(any<BaseEvent>(), any())
        }
    }

    @Test
    fun `amplitude request NOT ignored`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.amplitude.com/test"
            )
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://api.amplitude.com/test", eventProperties[NETWORK_TRACKING_URL]
                    )
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
        }
    }

    @Test
    fun `wildcard host matching`() {
        val plugin = networkTrackingPlugin(
            overrideCaptureRules = listOf(
                CaptureRule(
                    hosts = listOf("*"),
                    statusCodeRange = (200..299).toList()
                )
            ),
            ignoreAmplitudeRequests = false
        )

        val matchingHosts = listOf(
            "api.example.com",
            "test.amplitude.com", // one amplitude host
            "random-api.com"
        )
        matchingHosts.forEach { host ->
            plugin.intercept(
                mockInterceptorChain(
                    statusCode = 200,
                    url = "https://$host/test"
                )
            )
        }

        verifyOrder {
            matchingHosts.forEach { host ->
                mockAmplitude.track(
                    eq(NETWORK_TRACKING),
                    withArg { eventProperties ->
                        assertEquals("https://$host/test", eventProperties[NETWORK_TRACKING_URL])
                        assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                        assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                    }
                )
            }
        }
    }

    @Test
    fun `specific host matching`() {
        val plugin = networkTrackingPlugin(
            overrideCaptureRules = listOf(
                CaptureRule(
                    hosts = listOf("api.example.com"),
                    statusCodeRange = (200..299).toList()
                )
            ),
            ignoreAmplitudeRequests = true
        )

        val nonMatchingHosts = listOf(
            "other.example.com",
            "test.amplitude.com", // one amplitude host
            "api.different.com",
        )

        // matching host request
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.example.com/test"
            )
        )

        // non-matching host requests
        nonMatchingHosts.forEach { host ->
            plugin.intercept(
                mockInterceptorChain(
                    statusCode = 200,
                    url = "https://$host/test"
                )
            )
        }

        verify(exactly = 1) {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://api.example.com/test", eventProperties[NETWORK_TRACKING_URL]
                    )
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
        }
    }

    @Test
    fun `Multiple Capture Rules`() {
        val plugin = networkTrackingPlugin(
            overrideCaptureRules = listOf(
                CaptureRule(
                    hosts = listOf("api1.example.com"),
                    statusCodeRange = (200..299).toList()
                ),
                CaptureRule(
                    hosts = listOf("api2.example.com"),
                    statusCodeRange = (400..499).toList()
                )
            )
        )

        // Should match first rule
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api1.example.com/test"
            )
        )

        // Should match second rule
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 404,
                url = "https://api2.example.com/test"
            )
        )

        // Should not match any rules
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api2.example.com/test"
            )
        )

        verifyOrder {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://api1.example.com/test", eventProperties[NETWORK_TRACKING_URL]
                    )
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://api2.example.com/test", eventProperties[NETWORK_TRACKING_URL]
                    )
                    assertEquals(404, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
        }
    }

    @Test
    fun `trackEvent property correctness`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        val url = "https://example.com/test?param1=value1&param2=value2&param3=123#fragment"
        plugin.intercept(
            mockInterceptorChain(
                200,
                url = url,
                requestBodyString = "{\"event_type\":\"other_event\"}"
            )
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(url, eventProperties[NETWORK_TRACKING_URL])
                    assertEquals(
                        "param1=value1&param2=value2&param3=123",
                        eventProperties[NETWORK_TRACKING_URL_QUERY]
                    )
                    assertEquals("fragment", eventProperties[NETWORK_TRACKING_URL_FRAGMENT])
                    assertEquals("POST", eventProperties[NETWORK_TRACKING_REQUEST_METHOD])
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertNull(eventProperties[NETWORK_TRACKING_ERROR_MESSAGE])
                    assertNotNull(eventProperties[NETWORK_TRACKING_START_TIME])
                    assertNotNull(eventProperties[NETWORK_TRACKING_COMPLETION_TIME])
                    assertNotNull(eventProperties[NETWORK_TRACKING_DURATION])
                    assertEquals(28L, eventProperties[NETWORK_TRACKING_REQUEST_BODY_SIZE])
                    assertEquals(2L, eventProperties[NETWORK_TRACKING_RESPONSE_BODY_SIZE])
                    assertEquals(11, eventProperties.size)
                }
            )
        }
    }

    @Test
    fun `trackEvent property correctness - exception`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (0..0) + (500..599),
            ignoreAmplitudeRequests = false
        )

        val exception = IOException("Network error")
        try {
            plugin.intercept(mockInterceptorChain(statusCode = 200, exception = exception))
        } catch (e: IOException) {
            assertEquals(e, exception)
        }

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertNull(eventProperties[NETWORK_TRACKING_URL_QUERY])
                    assertNull(eventProperties[NETWORK_TRACKING_URL_FRAGMENT])
                    assertEquals("POST", eventProperties[NETWORK_TRACKING_REQUEST_METHOD])
                    assertNull(eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals("Network error", eventProperties[NETWORK_TRACKING_ERROR_MESSAGE])
                    assertNotNull(eventProperties[NETWORK_TRACKING_START_TIME])
                    assertNotNull(eventProperties[NETWORK_TRACKING_COMPLETION_TIME])
                    assertNotNull(eventProperties[NETWORK_TRACKING_DURATION])
                    assertEquals(2L, eventProperties[NETWORK_TRACKING_REQUEST_BODY_SIZE])
                    assertNull(eventProperties[NETWORK_TRACKING_RESPONSE_BODY_SIZE])
                }
            )
        }
    }

    @Test
    fun `large request body`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        val largeBody = "a".repeat(10_000)
        plugin.intercept(mockInterceptorChain(200, requestBodyString = largeBody))

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                    assertEquals(10_000L, eventProperties[NETWORK_TRACKING_REQUEST_BODY_SIZE])
                }
            )
        }
    }

    @Test
    fun `empty request body`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(mockInterceptorChain(200, requestBodyString = ""))

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                    assertEquals(0L, eventProperties[NETWORK_TRACKING_REQUEST_BODY_SIZE])
                }
            )
        }
    }

    @Test
    fun `only capture amplitude API requests`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://test.amplitude.com/api",
                requestBodyString = "{\"event_type\":\"other_event\"}"
            )
        )

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://test.amplitude.com/api",
                requestBodyString = "{\"event_type\":\"$NETWORK_TRACKING\"}"
            )
        )

        // we're expecting only the API event to be tracked,
        // as we want to skip our own network tracking events
        verify(exactly = 1) {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://test.amplitude.com/api", eventProperties[NETWORK_TRACKING_URL]
                    )
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
        }
    }

    @Test
    fun `network tracking events are NOT captured`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.example.com/test",
                requestBodyString = "{\"event_type\":\"$NETWORK_TRACKING\"}"
            )
        )

        verify(exactly = 0) {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://api.example.com/test", eventProperties[NETWORK_TRACKING_URL]
                    )
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                }
            )
        }
    }

    @Test
    fun `mask sensitive URL params`() {
        val plugin = networkTrackingPlugin(
            statusCodeRange = (200..299).toList(),
            ignoreAmplitudeRequests = false
        )

        val url = "https://sample_username:sample_password@example.com/test?username=johndoe&password=1234&email=test@example.com&phone=1234567890&token=abcd1234"
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = url
            )
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://mask:mask@example.com/test?username=[mask]&password=[mask]&email=[mask]&phone=[mask]&token=abcd1234",
                        eventProperties[NETWORK_TRACKING_URL]
                    )
                }
            )
        }
    }

    private fun mockInterceptorChain(
        statusCode: Int,
        exception: IOException? = null,
        url: String = "https://example.com/test",
        requestBodyString: String = "{}",
    ): Chain {
        val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
        val mockRequest = Builder()
            .post(requestBody)
            .url(url)
            .build()

        val statusMessage = if (statusCode in 200..299) "OK" else "Error"
        val emptyResponseBody = "{}".toResponseBody("application/json".toMediaType())
        val mockResponse = Response.Builder()
            .request(mockRequest)
            .protocol(HTTP_1_1)
            .body(emptyResponseBody)
            .code(statusCode)
            .message(statusMessage)
            .build()

        val mockChain = mockk<Chain> {
            every { request() } returns mockRequest
            if (exception == null) {
                every { proceed(mockRequest) } returns mockResponse
            } else {
                every { proceed(mockRequest) } throws exception
            }
        }
        return mockChain
    }
}