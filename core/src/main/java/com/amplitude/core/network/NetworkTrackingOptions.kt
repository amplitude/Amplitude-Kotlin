package com.amplitude.core.network

import com.amplitude.core.network.NetworkTrackingOptions.CaptureRule
import kotlin.text.RegexOption.IGNORE_CASE

private const val STAR_WILDCARD = "*"

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

    data class CaptureRule(
        val hosts: List<String>,
        val statusCodeRange: List<Int> = (500..599).toList(),
    ) {
        private val hostMatcher = HostMatcher(hosts)

        fun matches(host: String): Boolean {
            return hostMatcher.matches(host)
        }
    }

    init {
        require(captureRules.all { it.hosts.isNotEmpty() }) {
            "Capture rules must have a non-empty host list."
        }
        require(captureRules.all { it.statusCodeRange.isNotEmpty() }) {
            "Capture rules must have a non-empty status code range."
        }
    }

    private val ignoreHostMatcher = HostMatcher(ignoreHosts)

    fun shouldIgnore(host: String): Boolean {
        return ignoreHostMatcher.matches(host)
    }
}

private class HostMatcher(hosts: List<String>) {
    private val hostRegexes: List<Regex> by lazy {
        hosts.filter { it.contains(STAR_WILDCARD) }
            .map { host ->
                val regexString =
                    if (host == STAR_WILDCARD) {
                        // Single wildcard matches everything
                        ".*"
                    } else {
                        // For domain or multiple wildcards, e.g. "*.example.com", "*.sub.*.example.com"
                        // it matches "api.example.com" but not "api.test.example.com"
                        // it matches "api.sub.domain.example.com" but not "api.test.sub.domain.example.com"
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

/**
 * Checks if the request matches any of the capture rules.
 *
 * @param host The host of the request URL.
 * @param responseCode The status code of the response, or null if it's an error.
 */
fun List<CaptureRule>.matches(
    host: String,
    responseCode: Int,
): Boolean {
    val ruleWithMatchingHost = lastOrNull { it.matches(host) } ?: return false
    return responseCode in ruleWithMatchingHost.statusCodeRange
}
