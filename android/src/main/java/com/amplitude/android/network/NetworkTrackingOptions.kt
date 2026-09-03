package com.amplitude.android.network

import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule
import com.amplitude.android.utilities.ObjectFilter
import okhttp3.Headers
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.text.RegexOption.IGNORE_CASE

private const val STAR_WILDCARD = "*"

internal val SAFE_HEADERS: Set<String> =
    setOf(
        "access-control-allow-origin",
        "access-control-allow-credentials",
        "access-control-expose-headers",
        "access-control-max-age",
        "access-control-allow-methods",
        "access-control-allow-headers",
        "accept-patch",
        "accept-ranges",
        "age",
        "allow",
        "alt-svc",
        "cache-control",
        "connection",
        "content-disposition",
        "content-encoding",
        "content-language",
        "content-length",
        "content-location",
        "content-md5",
        "content-range",
        "content-type",
        "date",
        "delta-base",
        "etag",
        "expires",
        "im",
        "last-modified",
        "link",
        "location",
        "permanent",
        "p3p",
        "pragma",
        "proxy-authenticate",
        "public-key-pins",
        "retry-after",
        "server",
        "status",
        "strict-transport-security",
        "trailer",
        "transfer-encoding",
        "tk",
        "upgrade",
        "vary",
        "via",
        "warning",
        "www-authenticate",
        "x-b3-traceid",
        "x-frame-options",
    )

internal val BLOCK_HEADERS: Set<String> =
    setOf(
        "authorization",
        "cookie",
        "proxy-authorization",
    )

public class NetworkTrackingOptions
    @JvmOverloads
    constructor(
        captureRules: List<CaptureRule>,
        ignoreHosts: List<String> = emptyList(),
        public val ignoreAmplitudeRequests: Boolean = true,
        public val enabled: Boolean = true,
        public val enableRemoteConfig: Boolean = true,
    ) {
    public val captureRules: List<CaptureRule> = captureRules.toList()
    public val ignoreHosts: List<String> = ignoreHosts.toList()

    public companion object {
        public val DEFAULT: NetworkTrackingOptions by lazy {
            NetworkTrackingOptions(
                captureRules =
                    listOf(
                        CaptureRule(
                            hosts = listOf(STAR_WILDCARD),
                        ),
                    ),
            )
        }
    }

    public class CaptureHeader
        @JvmOverloads
        constructor(
            allowlist: List<String> = emptyList(),
            public val captureSafeHeaders: Boolean = true,
        ) {
        public val allowlist: List<String> = allowlist.toList()

        internal fun filterHeaders(headers: Headers): Map<String, Any>? {
            val combinedAllowSet =
                buildSet {
                    addAll(allowlist.map { it.lowercase() })
                    if (captureSafeHeaders) addAll(SAFE_HEADERS)
                    removeAll(BLOCK_HEADERS)
                }
            if (combinedAllowSet.isEmpty()) return null
            val result = linkedMapOf<String, Any>()
            for (i in 0 until headers.size) {
                val name = headers.name(i)
                if (!combinedAllowSet.contains(name.lowercase())) continue
                val value = headers.value(i)
                when (val existing = result[name]) {
                    null -> result[name] = value
                    is String -> result[name] = mutableListOf(existing, value)
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        (existing as MutableList<String>).add(value)
                    }
                }
            }
            return result.ifEmpty { null }
        }
    }

    public class CaptureBody
        @JvmOverloads
        constructor(
            allowlist: List<String>,
            excludelist: List<String> = emptyList(),
        ) {
        public val allowlist: List<String> = allowlist.toList()
        public val excludelist: List<String> = excludelist.toList()
        private val objectFilter = ObjectFilter(this.allowlist, this.excludelist)

        internal fun filterBodyBytes(bodyBytes: ByteArray?): String? {
            if (bodyBytes == null || bodyBytes.isEmpty()) return null
            return try {
                val bodyString = bodyBytes.toString(Charsets.UTF_8)
                val json: Any =
                    when (val parsed = JSONTokener(bodyString).nextValue()) {
                        is JSONObject -> jsonObjectToMap(parsed)
                        is JSONArray -> jsonArrayToList(parsed)
                        else -> return null
                    }
                val filtered = objectFilter.filtered(json) ?: return null
                when (filtered) {
                    is Map<*, *> -> JSONObject(filtered).toString()
                    is List<*> -> JSONArray(filtered).toString()
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    public sealed class URLPattern {
        public data class Exact(val url: String) : URLPattern()

        public data class Regex(val pattern: String) : URLPattern()
    }

    public class CaptureRule internal constructor(
        hosts: List<String>,
        urls: List<URLPattern>,
        methods: List<String>,
        statusCodeRange: List<Int>,
        public val requestHeaders: CaptureHeader?,
        public val responseHeaders: CaptureHeader?,
        public val requestBody: CaptureBody?,
        public val responseBody: CaptureBody?,
    ) {
        public val hosts: List<String> = hosts.toList()
        public val urls: List<URLPattern> = urls.toList()
        public val methods: List<String> = methods.toList()
        public val statusCodeRange: List<Int> = statusCodeRange.toList()

        /**
         * Creates a rule that matches requests by host patterns.
         */
        @JvmOverloads
        public constructor(
            hosts: List<String>,
            statusCodeRange: List<Int> = (500..599).toList(),
        ) : this(
            hosts = hosts,
            urls = emptyList(),
            methods = emptyList(),
            statusCodeRange = statusCodeRange,
            requestHeaders = null,
            responseHeaders = null,
            requestBody = null,
            responseBody = null,
        )

        /**
         * Creates a rule that matches requests by URL patterns (exact or regex).
         */
        public constructor(
            urls: List<URLPattern>,
            methods: List<String> = emptyList(),
            statusCodeRange: List<Int> = (500..599).toList(),
            requestHeaders: CaptureHeader? = null,
            responseHeaders: CaptureHeader? = null,
            requestBody: CaptureBody? = null,
            responseBody: CaptureBody? = null,
        ) : this(
            hosts = emptyList(),
            urls = urls,
            methods = methods,
            statusCodeRange = statusCodeRange,
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
            requestBody = requestBody,
            responseBody = responseBody,
        )

        private val hostMatcher = HostMatcher(hosts)
        private val urlMatchers: List<Pair<URLPattern, Regex?>> =
            urls.map { pattern ->
                when (pattern) {
                    is URLPattern.Exact -> pattern to null
                    is URLPattern.Regex -> pattern to pattern.pattern.toRegex()
                }
            }

        internal fun matchesRequest(
            host: String,
            url: String,
            method: String?,
        ): Boolean {
            if (urls.isNotEmpty()) {
                if (!matchesUrl(url)) return false
            } else if (hosts.isNotEmpty()) {
                if (!hostMatcher.matches(host)) return false
            } else {
                return false
            }

            if (methods.isNotEmpty() && !methods.contains("*")) {
                val upperMethod = method?.uppercase() ?: return false
                if (methods.none { it.equals(upperMethod, ignoreCase = true) }) return false
            }

            return true
        }

        private fun matchesUrl(url: String): Boolean {
            return urlMatchers.any { (pattern, regex) ->
                when (pattern) {
                    is URLPattern.Exact -> pattern.url == url
                    is URLPattern.Regex -> regex?.containsMatchIn(url) == true
                }
            }
        }
    }

    init {
        require(captureRules.all { it.hosts.isNotEmpty() || it.urls.isNotEmpty() }) {
            "Capture rules must have a non-empty host list or URL list."
        }
        require(captureRules.all { it.statusCodeRange.isNotEmpty() }) {
            "Capture rules must have a non-empty status code range."
        }
    }

    private val ignoreHostMatcher = HostMatcher(ignoreHosts)

    internal fun shouldIgnore(host: String): Boolean {
        if (ignoreAmplitudeRequests && host.isAmplitudeHost()) return true
        return ignoreHostMatcher.matches(host)
    }
}

private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    for (key in obj.keys()) {
        map[key] = convertJsonValue(obj.get(key))
    }
    return map
}

private fun jsonArrayToList(arr: JSONArray): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until arr.length()) {
        list.add(convertJsonValue(arr.get(i)))
    }
    return list
}

private fun convertJsonValue(value: Any?): Any? {
    return when (value) {
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        JSONObject.NULL -> null
        else -> value
    }
}

internal fun String.isAmplitudeHost(): Boolean {
    val lower = lowercase()
    return lower == "amplitude.com" || lower.endsWith(".amplitude.com")
}

internal class HostMatcher(hosts: List<String>) {
    private val hostRegexes: List<Regex> by lazy {
        hosts.filter { it.contains(STAR_WILDCARD) }
            .map { host ->
                val regexString =
                    if (host == STAR_WILDCARD) {
                        ".*"
                    } else {
                        host
                            .replace(".", "\\.")
                            .replace(STAR_WILDCARD, "[^.]+")
                    }
                "^$regexString$".toRegex(IGNORE_CASE)
            }
    }
    private val hostSet: Set<String> by lazy {
        hosts.filter { !it.contains("*") }
            .map { it.lowercase() }
            .toSet()
    }

    fun matches(host: String): Boolean {
        return hostSet.contains(host.lowercase()) || hostRegexes.any { it.matches(host) }
    }
}

internal fun List<CaptureRule>.findMatchingRule(
    host: String,
    url: String,
    method: String?,
    responseCode: Int,
): CaptureRule? {
    val matchingRule = lastOrNull { it.matchesRequest(host, url, method) } ?: return null
    return if (responseCode in matchingRule.statusCodeRange) matchingRule else null
}
