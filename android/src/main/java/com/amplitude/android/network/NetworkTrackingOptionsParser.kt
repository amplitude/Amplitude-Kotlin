package com.amplitude.android.network

import com.amplitude.android.network.NetworkTrackingOptions.CaptureBody
import com.amplitude.android.network.NetworkTrackingOptions.CaptureHeader
import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule
import com.amplitude.android.network.NetworkTrackingOptions.URLPattern

/**
 * Parses [CaptureRule] lists from remote config maps.
 *
 * Uses all-or-nothing semantics: if any rule fails to parse, the entire
 * override is rejected and `null` is returned.
 */
@Suppress("UNCHECKED_CAST")
internal fun parseCaptureRulesFromRemoteConfig(configs: List<Map<String, Any?>>): List<CaptureRule>? {
    if (configs.isEmpty()) return null
    return try {
        configs.map { map ->
            CaptureRule(
                hosts = (map["hosts"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                urls = parseUrlPatterns(map),
                methods = (map["methods"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                statusCodeRange = parseStatusCodeRange(map["statusCodeRange"] as? String ?: "500-599"),
                requestHeaders = parseCaptureHeader(map["requestHeaders"] as? Map<String, Any?>),
                responseHeaders = parseCaptureHeader(map["responseHeaders"] as? Map<String, Any?>),
                requestBody = parseCaptureBody(map["requestBody"] as? Map<String, Any?>),
                responseBody = parseCaptureBody(map["responseBody"] as? Map<String, Any?>),
            )
        }
    } catch (_: Exception) {
        null
    }
}

private fun parseUrlPatterns(map: Map<String, Any?>): List<URLPattern> {
    val patterns = mutableListOf<URLPattern>()
    (map["urls"] as? List<*>)?.filterIsInstance<String>()?.forEach {
        patterns.add(URLPattern.Exact(it))
    }
    (map["urlsRegex"] as? List<*>)?.filterIsInstance<String>()?.forEach {
        patterns.add(URLPattern.Regex(it))
    }
    return patterns
}

internal fun parseStatusCodeRange(rangeString: String): List<Int> {
    val codes = mutableListOf<Int>()
    for (part in rangeString.split(",")) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val (lo, hi) = trimmed.split("-", limit = 2).map { it.trim().toInt() }
            codes.addAll(lo..hi)
        } else {
            codes.add(trimmed.toInt())
        }
    }
    return codes
}

private fun parseCaptureHeader(map: Map<String, Any?>?): CaptureHeader? {
    if (map == null) return null
    return CaptureHeader(
        allowlist = (map["allowlist"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        captureSafeHeaders = map["captureSafeHeaders"] as? Boolean ?: true,
    )
}

private fun parseCaptureBody(map: Map<String, Any?>?): CaptureBody? {
    if (map == null) return null
    return CaptureBody(
        allowlist = (map["allowlist"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        excludelist = (map["excludelist"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
    )
}
