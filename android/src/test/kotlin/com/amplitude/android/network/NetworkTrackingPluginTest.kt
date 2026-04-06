package com.amplitude.android.network

import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_COMPLETION_TIME
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_DURATION
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_ERROR_MESSAGE
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY_SIZE
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_HEADERS
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_METHOD
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY_SIZE
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_HEADERS
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_START_TIME
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_STATUS_CODE
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_FRAGMENT
import com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_QUERY
import com.amplitude.android.Constants.EventTypes.NETWORK_TRACKING
import com.amplitude.android.network.NetworkTrackingOptions.CaptureBody
import com.amplitude.android.network.NetworkTrackingOptions.CaptureHeader
import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule
import com.amplitude.android.network.NetworkTrackingOptions.URLPattern
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import okhttp3.Interceptor.Chain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol.HTTP_1_1
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkTrackingPluginTest {
    private val mockAmplitude = mockk<Amplitude>(relaxed = true)

    private fun networkTrackingPlugin(
        overrideCaptureRules: List<CaptureRule>? = null,
        hosts: List<String> = listOf("example.com"),
        statusCodeRange: List<Int> = emptyList(),
        ignoreHosts: List<String> = emptyList(),
        ignoreAmplitudeRequests: Boolean = true,
    ) = NetworkTrackingPlugin(
        NetworkTrackingOptions(
            captureRules =
                overrideCaptureRules ?: listOf(
                    CaptureRule(
                        hosts = hosts,
                        statusCodeRange = statusCodeRange,
                    ),
                ),
            ignoreHosts = ignoreHosts,
            ignoreAmplitudeRequests = ignoreAmplitudeRequests,
        ),
    ).apply { amplitude = mockAmplitude }

    @Test
    fun `successful response capture`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        plugin.intercept(mockInterceptorChain(200))

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                },
            )
        }
    }

    @Test
    fun `successful response outside capture range - does not trigger trackEvent`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        plugin.intercept(mockInterceptorChain(404))

        verify(exactly = 0) {
            mockAmplitude.track(any<BaseEvent>(), any())
        }
    }

    @Test
    fun `failed response capture`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = listOf(500),
                ignoreAmplitudeRequests = false,
            )

        plugin.intercept(mockInterceptorChain(statusCode = 500))

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertEquals(500, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                },
            )
        }
    }

    @Test
    fun `failed response outside capture range - does not trigger trackEvent`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (400..499).toList(),
                ignoreAmplitudeRequests = false,
            )

        plugin.intercept(mockInterceptorChain(500))

        verify(exactly = 0) {
            mockAmplitude.track(any<BaseEvent>(), any())
        }
    }

    @Test
    fun `local exception capture`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (0..0) + (500..599),
                ignoreAmplitudeRequests = false,
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
                },
            )
        }
    }

    @Test
    fun `ignore hosts`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
                ignoreHosts = listOf("ignore.example.com", "*.ignore.com"),
            )

        // Test exact match ignored host
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://ignore.example.com/test",
            ),
        )

        // Test wildcard match ignored host
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.ignore.com/test",
            ),
        )

        // Test accepted/matched host
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://example.com/test",
            ),
        )

        // Verify only non-ignored host request was tracked
        verify(exactly = 1) {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://example.com/test",
                        eventProperties[NETWORK_TRACKING_URL],
                    )
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                },
            )
        }
    }

    @Test
    fun `amplitude request ignored when ignoreAmplitudeRequests is true`() {
        val plugin =
            networkTrackingPlugin(
                hosts = listOf("*"),
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = true,
            )

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.amplitude.com/test",
            ),
        )

        // Also verify EU endpoint is ignored
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.eu.amplitude.com/test",
            ),
        )

        verify(exactly = 0) {
            mockAmplitude.track(any<BaseEvent>(), any())
        }
    }

    @Test
    fun `amplitude request NOT ignored when ignoreAmplitudeRequests is false`() {
        val plugin =
            networkTrackingPlugin(
                hosts = listOf("*"),
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.amplitude.com/test",
                requestBodyString = "{\"event_type\":\"other_event\"}",
            ),
        )

        verify(exactly = 1) {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://api.amplitude.com/test",
                        eventProperties[NETWORK_TRACKING_URL],
                    )
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                },
            )
        }
    }

    @Test
    fun `multiple capture rules`() {
        val plugin =
            networkTrackingPlugin(
                overrideCaptureRules =
                    listOf(
                        CaptureRule(
                            hosts = listOf("*"),
                            statusCodeRange = (500..599).toList(),
                        ),
                        CaptureRule(
                            hosts = listOf("api1.example.com"),
                            statusCodeRange = (200..299).toList(),
                        ),
                        CaptureRule(
                            hosts = listOf("api2.example.com"),
                            statusCodeRange = (400..499).toList(),
                        ),
                    ),
                ignoreAmplitudeRequests = true,
            )

        // Should match second rule
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api1.example.com/test",
            ),
        )
        // Should match third rule
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 404,
                url = "https://api2.example.com/test",
            ),
        )
        // Should match first rule (wildcard)
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 503,
                url = "https://any.domain.com/test",
            ),
        )

        // Should not match any rules (wrong status code ranges)
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api2.example.com/test",
            ),
        )
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 404,
                url = "https://api1.example.com/test",
            ),
        )

        verifyOrder {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://api1.example.com/test",
                        eventProperties[NETWORK_TRACKING_URL],
                    )
                    assertEquals(200, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                },
            )
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://api2.example.com/test",
                        eventProperties[NETWORK_TRACKING_URL],
                    )
                    assertEquals(404, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                },
            )
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://any.domain.com/test",
                        eventProperties[NETWORK_TRACKING_URL],
                    )
                    assertEquals(503, eventProperties[NETWORK_TRACKING_STATUS_CODE])
                    assertEquals(true, eventProperties[NETWORK_TRACKING_DURATION] != null)
                },
            )
        }
        confirmVerified(mockAmplitude)
    }

    @Test
    fun `trackEvent property correctness`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        val url = "https://example.com/test?param1=value1&param2=value2&param3=123#fragment"
        plugin.intercept(
            mockInterceptorChain(
                200,
                url = url,
                requestBodyString = "{\"event_type\":\"other_event\"}",
            ),
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://example.com/test", eventProperties[NETWORK_TRACKING_URL])
                    assertEquals(
                        "param1=value1&param2=value2&param3=123",
                        eventProperties[NETWORK_TRACKING_URL_QUERY],
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
                    assertEquals(15, eventProperties.size)
                },
            )
        }
    }

    @Test
    fun `trackEvent property correctness - exception`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (0..0) + (500..599),
                ignoreAmplitudeRequests = false,
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
                },
            )
        }
    }

    @Test
    fun `large request body`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
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
                },
            )
        }
    }

    @Test
    fun `empty request body`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
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
                },
            )
        }
    }

    @Test
    fun `network tracking events to amplitude host are NOT captured - recursion guard`() {
        val plugin =
            networkTrackingPlugin(
                hosts = listOf("*"),
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        // Recursion guard only applies to amplitude hosts
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api2.amplitude.com/2/httpapi",
                requestBodyString = "{\"event_type\":\"$NETWORK_TRACKING\"}",
            ),
        )

        verify(exactly = 0) {
            mockAmplitude.track(any<BaseEvent>(), any())
        }
    }

    @Test
    fun `network tracking event type in body on non-amplitude host is captured normally`() {
        val plugin =
            networkTrackingPlugin(
                hosts = listOf("*"),
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        // Non-amplitude host: body is NOT scanned, so the event IS captured
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = "https://api.example.com/test",
                requestBodyString = "{\"event_type\":\"$NETWORK_TRACKING\"}",
            ),
        )

        verify(exactly = 1) {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals("https://api.example.com/test", eventProperties[NETWORK_TRACKING_URL])
                },
            )
        }
    }

    @Test
    fun `mask sensitive URL params with fragment`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        val url = "https://example.com/test?username=johndoe&password=1234#email"
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = url,
            ),
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://example.com/test",
                        eventProperties[NETWORK_TRACKING_URL],
                    )
                    assertEquals(
                        "username=[mask]&password=[mask]",
                        eventProperties[NETWORK_TRACKING_URL_QUERY],
                    )
                    assertEquals(
                        "email",
                        eventProperties[NETWORK_TRACKING_URL_FRAGMENT],
                    )
                },
            )
        }
    }

    @Test
    fun `mask sensitive URL params with empty query`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        val url = "https://example.com/test"
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = url,
            ),
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://example.com/test",
                        eventProperties[NETWORK_TRACKING_URL],
                    )
                },
            )
        }
    }

    @Test
    fun `mask sensitive URL params with credentials only`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        val url = "https://sample_username:sample_password@example.com/test"
        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                url = url,
            ),
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(
                        "https://mask:mask@example.com/test",
                        eventProperties[NETWORK_TRACKING_URL],
                    )
                },
            )
        }
    }

    // --- Header and Body Capture Tests ---

    @Test
    fun `capture request and response headers`() {
        val captureHeader =
            CaptureHeader(
                allowlist = listOf("x-custom"),
                captureSafeHeaders = true,
            )
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules =
                        listOf(
                            CaptureRule(
                                urls = listOf(URLPattern.Regex(".*example\\.com.*")),
                                statusCodeRange = (200..299).toList(),
                                requestHeaders = captureHeader,
                                responseHeaders = captureHeader,
                            ),
                        ),
                    ignoreAmplitudeRequests = false,
                ),
            ).apply { amplitude = mockAmplitude }

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                requestHeaders = mapOf("X-Custom" to "req-value", "Content-Type" to "application/json", "Authorization" to "Bearer token"),
                responseHeaders = mapOf("X-Custom" to "resp-value", "Cache-Control" to "no-cache", "Cookie" to "session=abc"),
            ),
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    @Suppress("UNCHECKED_CAST")
                    val reqHeaders = eventProperties[NETWORK_TRACKING_REQUEST_HEADERS] as Map<String, Any>
                    assertEquals("req-value", reqHeaders["X-Custom"])
                    assertEquals("application/json", reqHeaders["Content-Type"])
                    assertFalse(reqHeaders.containsKey("Authorization")) // blocked

                    @Suppress("UNCHECKED_CAST")
                    val respHeaders = eventProperties[NETWORK_TRACKING_RESPONSE_HEADERS] as Map<String, Any>
                    assertEquals("resp-value", respHeaders["X-Custom"])
                    assertEquals("no-cache", respHeaders["Cache-Control"])
                    assertFalse(respHeaders.containsKey("Cookie")) // blocked
                },
            )
        }
    }

    @Test
    fun `capture request and response body`() {
        val captureBody = CaptureBody(allowlist = listOf("user/name"))
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules =
                        listOf(
                            CaptureRule(
                                urls = listOf(URLPattern.Regex(".*example\\.com.*")),
                                statusCodeRange = (200..299).toList(),
                                requestBody = captureBody,
                                responseBody = captureBody,
                            ),
                        ),
                    ignoreAmplitudeRequests = false,
                ),
            ).apply { amplitude = mockAmplitude }

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                requestBodyString = """{"user":{"name":"John","password":"secret"}}""",
                responseBodyString = """{"user":{"name":"Jane","email":"jane@example.com"}}""",
            ),
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    val reqBody = eventProperties[NETWORK_TRACKING_REQUEST_BODY] as String
                    assertTrue(reqBody.contains("John"))
                    assertFalse(reqBody.contains("secret"))

                    val respBody = eventProperties[NETWORK_TRACKING_RESPONSE_BODY] as String
                    assertTrue(respBody.contains("Jane"))
                    assertFalse(respBody.contains("jane@example.com"))
                },
            )
        }
    }

    @Test
    fun `no header body config - same 11 properties`() {
        val plugin =
            networkTrackingPlugin(
                statusCodeRange = (200..299).toList(),
                ignoreAmplitudeRequests = false,
            )

        plugin.intercept(mockInterceptorChain(200))

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertEquals(15, eventProperties.size)
                    assertNull(eventProperties[NETWORK_TRACKING_REQUEST_HEADERS])
                    assertNull(eventProperties[NETWORK_TRACKING_RESPONSE_HEADERS])
                    assertNull(eventProperties[NETWORK_TRACKING_REQUEST_BODY])
                    assertNull(eventProperties[NETWORK_TRACKING_RESPONSE_BODY])
                },
            )
        }
    }

    @Test
    fun `non-JSON body - body property absent`() {
        val captureBody = CaptureBody(allowlist = listOf("key"))
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules =
                        listOf(
                            CaptureRule(
                                urls = listOf(URLPattern.Regex(".*example\\.com.*")),
                                statusCodeRange = (200..299).toList(),
                                requestBody = captureBody,
                                responseBody = captureBody,
                            ),
                        ),
                    ignoreAmplitudeRequests = false,
                ),
            ).apply { amplitude = mockAmplitude }

        plugin.intercept(
            mockInterceptorChain(
                statusCode = 200,
                requestBodyString = "not json content",
                responseBodyString = "also not json",
            ),
        )

        verify {
            mockAmplitude.track(
                eq(NETWORK_TRACKING),
                withArg { eventProperties ->
                    assertNull(eventProperties[NETWORK_TRACKING_REQUEST_BODY])
                    assertNull(eventProperties[NETWORK_TRACKING_RESPONSE_BODY])
                },
            )
        }
    }

    private fun mockInterceptorChain(
        statusCode: Int,
        exception: IOException? = null,
        url: String = "https://example.com/test",
        requestBodyString: String = "{}",
        responseBodyString: String = "{}",
        requestHeaders: Map<String, String> = emptyMap(),
        responseHeaders: Map<String, String> = emptyMap(),
    ): Chain {
        val requestBody = requestBodyString.toRequestBody("application/json".toMediaType())
        val requestBuilder =
            Builder()
                .post(requestBody)
                .url(url)
        requestHeaders.forEach { (key, value) -> requestBuilder.addHeader(key, value) }
        val mockRequest = requestBuilder.build()

        val statusMessage = if (statusCode in 200..299) "OK" else "Error"
        val mockResponseBody = responseBodyString.toResponseBody("application/json".toMediaType())
        val responseBuilder =
            Response.Builder()
                .request(mockRequest)
                .protocol(HTTP_1_1)
                .body(mockResponseBody)
                .code(statusCode)
                .message(statusMessage)
        responseHeaders.forEach { (key, value) -> responseBuilder.addHeader(key, value) }
        val mockResponse = responseBuilder.build()

        val mockChain =
            mockk<Chain> {
                every { request() } returns mockRequest
                if (exception == null) {
                    every { proceed(mockRequest) } returns mockResponse
                } else {
                    every { proceed(mockRequest) } throws exception
                }
            }
        return mockChain
    }

    // --- Remote Config Tests ---

    private class TestRemoteConfigClient : RemoteConfigClient {
        val callbacks = mutableListOf<RemoteConfigClient.RemoteConfigCallback>()

        override fun subscribe(
            key: RemoteConfigClient.Key,
            callback: RemoteConfigClient.RemoteConfigCallback,
        ) {
            if (key == RemoteConfigClient.Key.ANALYTICS_SDK) callbacks.add(callback)
        }

        override fun updateConfigs() {}

        fun emit(config: ConfigMap) {
            callbacks.forEach { it.onUpdate(config, RemoteConfigClient.Source.REMOTE, System.currentTimeMillis()) }
        }
    }

    private fun mockAndroidAmplitude(
        remoteConfigClient: RemoteConfigClient,
        enableAutocaptureRemoteConfig: Boolean = true,
    ): com.amplitude.android.Amplitude {
        val mockConfig = mockk<com.amplitude.android.Configuration>(relaxed = true)
        every { mockConfig.enableAutocaptureRemoteConfig } returns enableAutocaptureRemoteConfig
        val amplitude = mockk<com.amplitude.android.Amplitude>(relaxed = true)
        every { amplitude.configuration } returns mockConfig
        every { amplitude.remoteConfigClient } returns remoteConfigClient
        return amplitude
    }

    private fun networkTrackingConfig(vararg entries: Pair<String, Any?>): ConfigMap {
        return mapOf("autocapture" to mapOf("networkTracking" to mapOf(*entries)))
    }

    @Test
    fun `setup subscribes to remote config when both flags true`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(captureRules = listOf(CaptureRule(hosts = listOf("*"))), enableRemoteConfig = true),
            )
        plugin.setup(mockAndroidAmplitude(remoteConfig, enableAutocaptureRemoteConfig = true))

        assertEquals(1, remoteConfig.callbacks.size)
    }

    @Test
    fun `setup does NOT subscribe when enableAutocaptureRemoteConfig is false`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(captureRules = listOf(CaptureRule(hosts = listOf("*"))), enableRemoteConfig = true),
            )
        plugin.setup(mockAndroidAmplitude(remoteConfig, enableAutocaptureRemoteConfig = false))

        assertEquals(0, remoteConfig.callbacks.size)
    }

    @Test
    fun `setup does NOT subscribe when enableRemoteConfig option is false`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(captureRules = listOf(CaptureRule(hosts = listOf("*"))), enableRemoteConfig = false),
            )
        plugin.setup(mockAndroidAmplitude(remoteConfig, enableAutocaptureRemoteConfig = true))

        assertEquals(0, remoteConfig.callbacks.size)
    }

    @Test
    fun `setup with core Amplitude skips subscription`() {
        val coreAmplitude = mockk<Amplitude>(relaxed = true)
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(captureRules = listOf(CaptureRule(hosts = listOf("*"))), enableRemoteConfig = true),
            )
        // Should not crash — setup returns early when amplitude is not android.Amplitude
        plugin.setup(coreAmplitude)
    }

    @Test
    fun `remote config disables tracking`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules = listOf(CaptureRule(hosts = listOf("*"), statusCodeRange = (200..299).toList())),
                    enabled = true,
                ),
            )
        val amplitude = mockAndroidAmplitude(remoteConfig)
        plugin.setup(amplitude)
        plugin.amplitude = amplitude

        // Disable via remote config
        remoteConfig.emit(networkTrackingConfig("enabled" to false))

        plugin.intercept(mockInterceptorChain(200))

        verify(exactly = 0) { amplitude.track(any<BaseEvent>(), any()) }
    }

    @Test
    fun `remote config re-enables tracking`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules = listOf(CaptureRule(hosts = listOf("*"), statusCodeRange = (200..299).toList())),
                    // Initially disabled
                    enabled = false,
                ),
            )
        val amplitude = mockAndroidAmplitude(remoteConfig)
        plugin.setup(amplitude)
        plugin.amplitude = amplitude

        // Enable via remote config
        remoteConfig.emit(networkTrackingConfig("enabled" to true))

        plugin.intercept(mockInterceptorChain(200))

        verify(exactly = 1) { amplitude.track(eq(NETWORK_TRACKING), any()) }
    }

    @Test
    fun `remote config updates ignoreHosts`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules = listOf(CaptureRule(hosts = listOf("*"), statusCodeRange = (200..299).toList())),
                    ignoreHosts = emptyList(),
                    ignoreAmplitudeRequests = false,
                ),
            )
        val amplitude = mockAndroidAmplitude(remoteConfig)
        plugin.setup(amplitude)
        plugin.amplitude = amplitude

        // Add ignore host via remote config
        remoteConfig.emit(networkTrackingConfig("ignoreHosts" to listOf("example.com")))

        plugin.intercept(mockInterceptorChain(200, url = "https://example.com/test"))

        verify(exactly = 0) { amplitude.track(any<BaseEvent>(), any()) }
    }

    @Test
    fun `remote config updates captureRules`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules = listOf(CaptureRule(hosts = listOf("*"), statusCodeRange = (500..599).toList())),
                    ignoreAmplitudeRequests = false,
                ),
            )
        val amplitude = mockAndroidAmplitude(remoteConfig)
        plugin.setup(amplitude)
        plugin.amplitude = amplitude

        // Override rules to capture 200s instead
        remoteConfig.emit(
            mapOf(
                "autocapture" to
                    mapOf(
                        "networkTracking" to
                            mapOf(
                                "captureRules" to
                                    listOf(
                                        mapOf("hosts" to listOf("*"), "statusCodeRange" to "200-299"),
                                    ),
                            ),
                    ),
            ),
        )

        plugin.intercept(mockInterceptorChain(200))
        verify(exactly = 1) { amplitude.track(eq(NETWORK_TRACKING), any()) }
    }

    @Test
    fun `missing networkTracking section resets to local config`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules = listOf(CaptureRule(hosts = listOf("*"), statusCodeRange = (200..299).toList())),
                    enabled = true,
                    ignoreAmplitudeRequests = false,
                ),
            )
        val amplitude = mockAndroidAmplitude(remoteConfig)
        plugin.setup(amplitude)
        plugin.amplitude = amplitude

        // First: disable via remote config
        remoteConfig.emit(networkTrackingConfig("enabled" to false))
        plugin.intercept(mockInterceptorChain(200))
        verify(exactly = 0) { amplitude.track(any<BaseEvent>(), any()) }

        // Then: emit config without networkTracking section → should reset to local (enabled=true)
        remoteConfig.emit(mapOf("autocapture" to mapOf("sessions" to true)))

        plugin.intercept(mockInterceptorChain(200))
        verify(exactly = 1) { amplitude.track(eq(NETWORK_TRACKING), any()) }
    }

    @Test
    fun `partial remote config uses fresh overlay on local`() {
        val remoteConfig = TestRemoteConfigClient()
        val localRules = listOf(CaptureRule(hosts = listOf("local.com"), statusCodeRange = (200..299).toList()))
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules = localRules,
                    ignoreHosts = listOf("local-ignore.com"),
                    ignoreAmplitudeRequests = false,
                ),
            )
        val amplitude = mockAndroidAmplitude(remoteConfig)
        plugin.setup(amplitude)
        plugin.amplitude = amplitude

        // First remote config: override ignoreHosts and add captureRules
        remoteConfig.emit(
            mapOf(
                "autocapture" to
                    mapOf(
                        "networkTracking" to
                            mapOf(
                                "ignoreHosts" to listOf("remote-ignore.com"),
                                "captureRules" to
                                    listOf(
                                        mapOf("hosts" to listOf("*"), "statusCodeRange" to "200-299"),
                                    ),
                            ),
                    ),
            ),
        )

        // Second remote config: only set ignoreHosts — captureRules should fall back to LOCAL, not previous remote
        remoteConfig.emit(networkTrackingConfig("ignoreHosts" to listOf("other.com")))

        // local.com should match (local rules restored), not "*" from previous remote
        val opts = plugin.currentOptions
        assertEquals(localRules.first().hosts, opts.captureRules.first().hosts)
        assertEquals(listOf("other.com"), opts.ignoreHosts)
    }

    @Test
    fun `malformed remote config falls back to local options`() {
        val remoteConfig = TestRemoteConfigClient()
        val plugin =
            NetworkTrackingPlugin(
                NetworkTrackingOptions(
                    captureRules = listOf(CaptureRule(hosts = listOf("*"), statusCodeRange = (200..299).toList())),
                    ignoreAmplitudeRequests = false,
                ),
            )
        val amplitude = mockAndroidAmplitude(remoteConfig)
        plugin.setup(amplitude)
        plugin.amplitude = amplitude

        // Remote config sends a rule with empty hosts and urls — would fail require() check
        remoteConfig.emit(
            mapOf(
                "autocapture" to
                    mapOf(
                        "networkTracking" to
                            mapOf(
                                "captureRules" to
                                    listOf(
                                        mapOf("statusCodeRange" to "200-299"),
                                    ),
                            ),
                    ),
            ),
        )

        // Should fall back to local options, not crash
        plugin.intercept(mockInterceptorChain(200))
        verify(exactly = 1) { amplitude.track(eq(NETWORK_TRACKING), any()) }
    }
}
