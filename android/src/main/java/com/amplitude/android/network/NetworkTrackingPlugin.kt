package com.amplitude.android.network

import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule
import com.amplitude.core.Amplitude
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_COMPLETION_TIME
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_DURATION
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_ERROR_MESSAGE
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY_SIZE
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_REQUEST_HEADERS
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_REQUEST_METHOD
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY_SIZE
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_HEADERS
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_START_TIME
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_STATUS_CODE
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_URL
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_URL_FRAGMENT
import com.amplitude.core.Constants.EventProperties.NETWORK_TRACKING_URL_QUERY
import com.amplitude.core.Constants.EventTypes.NETWORK_TRACKING
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.Plugin.Type
import com.amplitude.core.platform.Plugin.Type.Utility
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.IOException

class NetworkTrackingPlugin(
    private val options: NetworkTrackingOptions = NetworkTrackingOptions.DEFAULT,
) : Interceptor, Plugin {
    override val type: Type = Utility
    override lateinit var amplitude: Amplitude

    override fun intercept(chain: Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        try {
            val response = chain.proceed(request)
            val matchedRule = matchedRule(request, response.code)
            if (matchedRule != null) {
                val completionTime = System.currentTimeMillis()
                trackEvent(request, response, startTime, completionTime, matchedRule = matchedRule)
            }
            return response
        } catch (error: IOException) {
            val matchedRule = matchedRule(request, LOCAL_ERROR_STATUS_CODE)
            if (matchedRule != null) {
                val completionTime = System.currentTimeMillis()
                trackEvent(request, null, startTime, completionTime, error, matchedRule = matchedRule)
            }
            throw error
        }
    }

    private fun matchedRule(
        request: Request,
        responseCode: Int,
    ): CaptureRule? {
        val host = request.url.host
        if (options.shouldIgnore(host)) return null
        // Recursion guard: only scan body for amplitude hosts to avoid consuming one-shot
        // bodies on normal requests. This prevents the SDK's own network tracking event
        // uploads from being re-tracked when the same OkHttpClient is shared.
        if (host.isAmplitudeHost() && request.body?.contains(NETWORK_TRACKING) == true) return null
        return options.captureRules.findMatchingRule(
            host,
            request.url.toString(),
            request.method,
            responseCode,
        )
    }

    /**
     * Checks if the request body contains the given search string without consuming the [RequestBody].
     */
    private fun RequestBody.contains(searchString: String): Boolean {
        val searchByteString = searchString.encodeToByteArray().toByteString()
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.indexOf(searchByteString) != -1L
    }

    private fun trackEvent(
        request: Request,
        response: Response?,
        startTime: Long,
        completionTime: Long,
        error: IOException? = null,
        matchedRule: CaptureRule,
    ) {
        val maskedHttpUrl = request.url.mask()

        // Filter headers
        val requestHeaders = matchedRule.requestHeaders?.filterHeaders(request.headers.toMap())
        val responseHeaders = matchedRule.responseHeaders?.let { captureHeader ->
            response?.headers?.let { captureHeader.filterHeaders(it.toMap()) }
        }

        // Filter request body — skip one-shot, duplex, and non-JSON bodies
        val requestBodyString = matchedRule.requestBody?.let { captureBody ->
            val body = request.body ?: return@let null
            if (body.isOneShot() || body.isDuplex()) return@let null

            val contentType = body.contentType()
            if (contentType != null && !(contentType.type == "application" && contentType.subtype.contains("json"))) {
                return@let null
            }

            try {
                val buffer = Buffer()
                body.writeTo(buffer)
                captureBody.filterBodyBytes(buffer.readByteArray())
            } catch (_: Exception) {
                null
            }
        }

        // Filter response body — bounded peek, JSON content type only
        val responseBodyString = matchedRule.responseBody?.let { captureBody ->
            val resp = response ?: return@let null
            if (!resp.isJsonContentType()) return@let null

            try {
                val peekedBody = resp.peekBody(MAX_BODY_PEEK_BYTES)
                captureBody.filterBodyBytes(peekedBody.bytes())
            } catch (_: Exception) {
                null
            }
        }

        amplitude.track(
            NETWORK_TRACKING,
            eventProperties =
                mapOf(
                    NETWORK_TRACKING_URL to maskedHttpUrl.toUrlOnlyString(),
                    NETWORK_TRACKING_URL_QUERY to maskedHttpUrl.query,
                    NETWORK_TRACKING_URL_FRAGMENT to maskedHttpUrl.fragment,
                    NETWORK_TRACKING_REQUEST_METHOD to request.method,
                    NETWORK_TRACKING_STATUS_CODE to response?.code,
                    NETWORK_TRACKING_ERROR_MESSAGE to error?.message,
                    NETWORK_TRACKING_START_TIME to startTime,
                    NETWORK_TRACKING_COMPLETION_TIME to completionTime,
                    NETWORK_TRACKING_DURATION to completionTime - startTime,
                    NETWORK_TRACKING_REQUEST_BODY_SIZE to request.body?.contentLength(),
                    NETWORK_TRACKING_RESPONSE_BODY_SIZE to response?.body?.contentLength(),
                    NETWORK_TRACKING_REQUEST_HEADERS to requestHeaders,
                    NETWORK_TRACKING_RESPONSE_HEADERS to responseHeaders,
                    NETWORK_TRACKING_REQUEST_BODY to requestBodyString,
                    NETWORK_TRACKING_RESPONSE_BODY to responseBodyString,
                ),
        )
    }

    private fun HttpUrl.mask(): HttpUrl {
        val sensitiveKeys = setOf("username", "password", "email", "phone")
        val query =
            queryParameterNames.joinToString("&") { name ->
                if (sensitiveKeys.contains(name.lowercase())) {
                    "$name=[mask]"
                } else {
                    "$name=${queryParameter(name)}"
                }
            }.ifBlank { null }

        return newBuilder()
            .username("mask".takeIf { username.isNotEmpty() } ?: "")
            .password("mask".takeIf { password.isNotEmpty() } ?: "")
            .query(query)
            .build()
    }

    private fun HttpUrl.toUrlOnlyString(): String {
        return mask().newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    companion object {
        private const val LOCAL_ERROR_STATUS_CODE = 0
        private const val MAX_BODY_PEEK_BYTES = 1L * 1024 * 1024 // 1 MB
    }
}

private fun Response.isJsonContentType(): Boolean {
    val contentType = body?.contentType() ?: return true // unknown type — attempt capture
    return contentType.type == "application" && contentType.subtype.contains("json")
}

private fun Headers.toMap(): Map<String, String> =
    (0 until size).associate { name(it) to value(it) }
