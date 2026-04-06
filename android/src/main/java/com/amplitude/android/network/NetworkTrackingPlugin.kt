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
import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.Plugin.Type
import com.amplitude.core.platform.Plugin.Type.Utility
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key
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
    options: NetworkTrackingOptions = NetworkTrackingOptions.DEFAULT,
) : Interceptor, Plugin {
    override val type: Type = Utility
    override lateinit var amplitude: Amplitude

    private val originalOptions: NetworkTrackingOptions = options

    @Volatile
    internal var currentOptions: NetworkTrackingOptions = options

    // Strong reference to prevent GC — RemoteConfigClient uses WeakReference
    private var remoteConfigCallback: RemoteConfigClient.RemoteConfigCallback? = null

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)

        val androidAmplitude = amplitude as? com.amplitude.android.Amplitude ?: return
        val androidConfig = androidAmplitude.configuration as? com.amplitude.android.Configuration ?: return

        if (androidConfig.enableAutocaptureRemoteConfig && originalOptions.enableRemoteConfig) {
            remoteConfigCallback =
                RemoteConfigClient.RemoteConfigCallback { config, _, _ ->
                    handleRemoteConfig(config)
                }
            androidAmplitude.remoteConfigClient.subscribe(Key.ANALYTICS_SDK, remoteConfigCallback!!)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleRemoteConfig(config: ConfigMap) {
        val autocaptureConfig = config["autocapture"] as? ConfigMap
        val networkConfig = autocaptureConfig?.get("networkTracking") as? ConfigMap

        if (networkConfig == null) {
            // No networkTracking section — reset to local config
            currentOptions = originalOptions
            return
        }

        // Fresh overlay on local options each time. Start from originalOptions so
        // fields absent from remote fall back to local, not to a previous remote value.
        var resolvedEnabled = originalOptions.enabled
        (networkConfig["enabled"] as? Boolean)?.let { resolvedEnabled = it }

        var ignoreHosts = originalOptions.ignoreHosts
        (networkConfig["ignoreHosts"] as? List<*>)?.filterIsInstance<String>()?.let {
            ignoreHosts = it
        }

        var ignoreAmplitudeRequests = originalOptions.ignoreAmplitudeRequests
        (networkConfig["ignoreAmplitudeRequests"] as? Boolean)?.let {
            ignoreAmplitudeRequests = it
        }

        var captureRules = originalOptions.captureRules
        (networkConfig["captureRules"] as? List<*>)?.let { rawRules ->
            val maps = rawRules.filterIsInstance<Map<String, Any?>>()
            val rules = parseCaptureRulesFromRemoteConfig(maps)
            if (rules != null) captureRules = rules
        }

        // Single atomic write — all fields in one snapshot.
        // Wrap in try-catch: malformed remote config (e.g. rules with empty hosts+urls)
        // would fail the require checks in NetworkTrackingOptions.init.
        currentOptions =
            try {
                NetworkTrackingOptions(
                    captureRules = captureRules,
                    ignoreHosts = ignoreHosts,
                    ignoreAmplitudeRequests = ignoreAmplitudeRequests,
                    enabled = resolvedEnabled,
                    enableRemoteConfig = originalOptions.enableRemoteConfig,
                )
            } catch (_: IllegalArgumentException) {
                originalOptions
            }
    }

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
        val opts = currentOptions // single read — enabled + options in one atomic snapshot
        if (!opts.enabled) return null
        val host = request.url.host
        if (opts.shouldIgnore(host)) return null
        // Recursion guard: only scan body for amplitude hosts to avoid consuming one-shot
        // bodies on normal requests.
        if (host.isAmplitudeHost() && request.body?.contains(NETWORK_TRACKING) == true) return null
        return opts.captureRules.findMatchingRule(
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
        val requestHeaders = matchedRule.requestHeaders?.filterHeaders(request.headers)
        val responseHeaders =
            matchedRule.responseHeaders?.let { captureHeader ->
                response?.headers?.let { captureHeader.filterHeaders(it) }
            }

        // Filter request body — skip one-shot, duplex, and non-JSON bodies
        val requestBodyString =
            matchedRule.requestBody?.let { captureBody ->
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
        val responseBodyString =
            matchedRule.responseBody?.let { captureBody ->
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
