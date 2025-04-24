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
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.Plugin.Type
import com.amplitude.core.platform.Plugin.Type.Utility
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.IOException

private const val STAR_WILDCARD = "*"
private const val AMPLITUDE_HOST_DOMAIN = "amplitude.com"
private const val LOCAL_ERROR_STATUS_CODE = 0

class NetworkTrackingPlugin(
    private val captureRules: List<CaptureRule> = listOf(
        CaptureRule(
            hosts = listOf(STAR_WILDCARD),
            statusCodeRange = (0..0) + (500..599),
        )
    ),
    private val ignoreHosts: List<String> = emptyList(),
    private val ignoreAmplitudeRequests: Boolean = true,
) : Interceptor, Plugin {
    override val type: Type = Utility
    override lateinit var amplitude: Amplitude

    init {
        require(captureRules.isNotEmpty()) {
            "Capture rules must not be empty."
        }
        require(captureRules.all { it.hosts.isNotEmpty() }) {
            "Capture rules must have a non-empty host list."
        }
        require(captureRules.all { it.statusCodeRange.isNotEmpty() }) {
            "Capture rules must have a non-empty status code range."
        }
    }

    override fun intercept(chain: Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        try {
            val response = chain.proceed(request)

            if (shouldCapture(request, response.code)) {
                val completionTime = System.currentTimeMillis()
                trackEvent(request, response, startTime, completionTime)
            }

            return response
        } catch (error: IOException) {
            if (shouldCapture(request, LOCAL_ERROR_STATUS_CODE)) {
                val completionTime = System.currentTimeMillis()
                trackEvent(request, null, startTime, completionTime, error)
            }

            throw error
        }
    }

    private fun shouldCapture(
        request: Request,
        responseCode: Int,
    ): Boolean {
        val host = request.url.host
        return when {
            host in ignoreHosts -> false
            ignoreAmplitudeRequests && request.amplitudeApiRequest() -> false
            captureRules.matches(host, responseCode) || request.amplitudeApiRequest() -> true
            else -> false
        }
    }

    /**
     * Checks if the request matches any of the capture rules.
     *
     * @param host The host of the request URL.
     * @param responseCode The status code of the response, or null if it's an error.
     */
    private fun List<CaptureRule>.matches(
        host: String,
        responseCode: Int,
    ): Boolean {
        return any { rule ->
            (host in rule.hosts || rule.hosts.contains(STAR_WILDCARD)) &&
                responseCode in rule.statusCodeRange
        }
    }

    /**
     * Checks if the request is an Amplitude request, and not a [NETWORK_TRACKING] event as we don't
     * want to track our own network tracking events to be tracked again for cases where the same
     * [okhttp3.OkHttpClient] instance is used for both Amplitude and other network requests.
     */
    private fun Request.amplitudeApiRequest(): Boolean {
        return url.host.endsWith(AMPLITUDE_HOST_DOMAIN) &&
            body?.contains(NETWORK_TRACKING) == false
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
    ) {
        amplitude.track(
            NETWORK_TRACKING,
            eventProperties = mapOf(
                NETWORK_TRACKING_URL to request.url.toString(),
                NETWORK_TRACKING_URL_QUERY to request.url.query,
                NETWORK_TRACKING_URL_FRAGMENT to request.url.fragment,
                NETWORK_TRACKING_REQUEST_METHOD to request.method,
                NETWORK_TRACKING_STATUS_CODE to response?.code,
                NETWORK_TRACKING_ERROR_MESSAGE to error?.message,
                NETWORK_TRACKING_START_TIME to startTime,
                NETWORK_TRACKING_COMPLETION_TIME to completionTime,
                NETWORK_TRACKING_DURATION to completionTime - startTime,
                NETWORK_TRACKING_REQUEST_BODY_SIZE to request.body?.contentLength(),
                NETWORK_TRACKING_RESPONSE_BODY_SIZE to response?.body?.contentLength(),
            )
        )
    }

    data class CaptureRule(
        val hosts: List<String>,
        val statusCodeRange: List<Int>,
    )
}
