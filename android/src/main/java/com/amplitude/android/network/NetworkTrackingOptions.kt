package com.amplitude.android.network

import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule
import com.amplitude.android.utilities.ObjectFilter
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.text.RegexOption.IGNORE_CASE

private const val STAR_WILDCARD = "*"

internal val SAFE_HEADERS: Set<String> = setOf(
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

internal val BLOCK_HEADERS: Set<String> = setOf(
    "authorization",
    "cookie",
    "proxy-authorization",
)

data class NetworkTrackingOptions(
    val captureRules: List<CaptureRule>,
    val ignoreHosts: List<String> = emptyList(),
    val ignoreAmplitudeRequests: Boolean = true,
) {
    companion object {
        val DEFAULT by lazy {
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

    data class CaptureHeader(
        val allowlist: List<String> = emptyList(),
        val captureSafeHeaders: Boolean = true,
    ) {
        internal fun filterHeaders(headers: Map<String, String>): Map<String, String>? {
            val combinedAllowSet = buildSet {
                addAll(allowlist.map { it.lowercase() })
                if (captureSafeHeaders) addAll(SAFE_HEADERS)
                removeAll(BLOCK_HEADERS)
            }
            if (combinedAllowSet.isEmpty()) return null
            val result = headers.filter { (key, _) -> combinedAllowSet.contains(key.lowercase()) }
            return result.ifEmpty { null }
        }
    }

    data class CaptureBody(
        val allowlist: List<String>,
        val blocklist: List<String> = emptyList(),
    ) {
        internal fun filterBodyBytes(bodyBytes: ByteArray?): String? {
            if (bodyBytes == null || bodyBytes.isEmpty()) return null
            return try {
                val bodyString = bodyBytes.toString(Charsets.UTF_8)
                val parsed = JSONTokener(bodyString).nextValue()
                val json: Any = when (parsed) {
                    is JSONObject -> jsonObjectToMap(parsed)
                    is JSONArray -> jsonArrayToList(parsed)
                    else -> return null
                }
                val filter = ObjectFilter(allowlist, blocklist)
                val filtered = filter.filtered(json) ?: return null
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

    sealed class URLPattern {
        data class Exact(val url: String) : URLPattern()
        data class Regex(val pattern: String) : URLPattern()
    }

    data class CaptureRule(
        val hosts: List<String> = emptyList(),
        val urls: List<URLPattern> = emptyList(),
        val methods: List<String> = emptyList(),
        val statusCodeRange: List<Int> = (500..599).toList(),
        val requestHeaders: CaptureHeader? = null,
        val responseHeaders: CaptureHeader? = null,
        val requestBody: CaptureBody? = null,
        val responseBody: CaptureBody? = null,
    ) {
        private val hostMatcher = HostMatcher(hosts)
        private val urlMatchers: List<Pair<URLPattern, Regex?>> =
            urls.map { pattern ->
                when (pattern) {
                    is URLPattern.Exact -> pattern to null
                    is URLPattern.Regex -> pattern to pattern.pattern.toRegex()
                }
            }

        internal fun matchesRequest(host: String, url: String, method: String?): Boolean {
            // If URLs are configured, match by URL patterns only
            if (urls.isNotEmpty()) {
                if (!matchesUrl(url)) return false
            } else if (hosts.isNotEmpty()) {
                if (!hostMatcher.matches(host)) return false
            } else {
                return false
            }

            // Check method matching
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
