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
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.toByteString
import okio.IOException

private const val AMPLITUDE_HOST_DOMAIN = "amplitude.com"
private const val LOCAL_ERROR_STATUS_CODE = 0

class NetworkTrackingPlugin(
    private val options: NetworkTrackingOptions = NetworkTrackingOptions.DEFAULT
) : Interceptor, Plugin {
    override val type: Type = Utility
    override lateinit var amplitude: Amplitude

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
    ): Boolean = with(options) {
        val host = request.url.host
        return when {
            shouldIgnore(host) -> false
            ignoreAmplitudeRequests && request.amplitudeApiRequest() -> false
            captureRules.matches(host, responseCode) || request.amplitudeApiRequest() -> true
            else -> false
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
        val maskedHttpUrl = request.url.mask()
        amplitude.track(
            eventType = NETWORK_TRACKING,
            eventProperties = mapOf(
                NETWORK_TRACKING_URL to maskedHttpUrl.toUrlOnlyString(),
                NETWORK_TRACKING_URL_QUERY to maskedHttpUrl.query.orEmpty(),
                NETWORK_TRACKING_URL_FRAGMENT to maskedHttpUrl.fragment.orEmpty(),
                NETWORK_TRACKING_REQUEST_METHOD to request.method,
                NETWORK_TRACKING_STATUS_CODE to (response?.code ?: 0),
                NETWORK_TRACKING_ERROR_MESSAGE to error?.message.orEmpty(),
                NETWORK_TRACKING_START_TIME to startTime,
                NETWORK_TRACKING_COMPLETION_TIME to completionTime,
                NETWORK_TRACKING_DURATION to completionTime - startTime,
                NETWORK_TRACKING_REQUEST_BODY_SIZE to (request.body?.contentLength() ?: 0L),
                NETWORK_TRACKING_RESPONSE_BODY_SIZE to (response?.body?.contentLength() ?: 0L),
            )
        )
    }

    /**
     * Masks sensitive information in the URL by replacing values of sensitive query parameters and
     * credentials with "[mask]". The following are considered sensitive:
     * - Username and password in URL credentials
     * - Query parameter values where the parameter name matches: username, password, email, phone
     *
     * Example:
     * Original URL: https://user:pass@example.com/path?email=test@email.com&id=123#password-reset
     * Masked URL: https://mask:mask@example.com/path?email=[mask]&id=123#password-reset
     *
     * Note: The fragment portion of the URL is preserved and not masked.
     *
     * @return A new [HttpUrl] with sensitive information masked
     */
    private fun HttpUrl.mask(): HttpUrl {
        val sensitiveKeys = setOf("username", "password", "email", "phone")
        val query = queryParameterNames.joinToString("&") { name ->
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
}
