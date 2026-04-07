package com.amplitude.android.network

import com.amplitude.android.network.NetworkTrackingOptions.URLPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkTrackingOptionsParserTest {
    @Test
    fun `full rule`() {
        val configs =
            listOf(
                mapOf(
                    "hosts" to listOf("api.example.com"),
                    "urls" to listOf("https://exact.com/path"),
                    "urlsRegex" to listOf(".*\\.example\\.com/api/.*"),
                    "methods" to listOf("GET", "POST"),
                    "statusCodeRange" to "200-299,500-599",
                    "requestHeaders" to mapOf("allowlist" to listOf("x-custom"), "captureSafeHeaders" to false),
                    "responseHeaders" to mapOf("allowlist" to listOf("x-resp"), "captureSafeHeaders" to true),
                    "requestBody" to mapOf("allowlist" to listOf("user/*"), "excludelist" to listOf("user/password")),
                    "responseBody" to mapOf("allowlist" to listOf("**")),
                ),
            )
        val rules = parseCaptureRulesFromRemoteConfig(configs)
        assertNotNull(rules)
        assertEquals(1, rules!!.size)
        val rule = rules[0]
        assertEquals(listOf("api.example.com"), rule.hosts)
        assertEquals(2, rule.urls.size)
        assertTrue(rule.urls[0] is URLPattern.Exact)
        assertTrue(rule.urls[1] is URLPattern.Regex)
        assertEquals(listOf("GET", "POST"), rule.methods)
        assertTrue(200 in rule.statusCodeRange)
        assertTrue(500 in rule.statusCodeRange)
        assertFalse(300 in rule.statusCodeRange)

        assertNotNull(rule.requestHeaders)
        assertEquals(listOf("x-custom"), rule.requestHeaders!!.allowlist)
        assertFalse(rule.requestHeaders!!.captureSafeHeaders)

        assertNotNull(rule.responseHeaders)
        assertTrue(rule.responseHeaders!!.captureSafeHeaders)

        assertNotNull(rule.requestBody)
        assertEquals(listOf("user/*"), rule.requestBody!!.allowlist)
        assertEquals(listOf("user/password"), rule.requestBody!!.excludelist)

        assertNotNull(rule.responseBody)
        assertEquals(listOf("**"), rule.responseBody!!.allowlist)
    }

    @Test
    fun `minimal rule`() {
        val configs = listOf(mapOf("hosts" to listOf("*")))
        val rules = parseCaptureRulesFromRemoteConfig(configs)
        assertNotNull(rules)
        assertEquals(listOf("*"), rules!![0].hosts)
        assertEquals((500..599).toList(), rules[0].statusCodeRange)
        assertNull(rules[0].requestHeaders)
        assertNull(rules[0].requestBody)
    }

    @Test
    fun `empty list returns null`() {
        assertNull(parseCaptureRulesFromRemoteConfig(emptyList()))
    }

    @Test
    fun `invalid status range rejects all rules`() {
        val configs =
            listOf(
                mapOf("hosts" to listOf("a.com"), "statusCodeRange" to "200-299"),
                mapOf("hosts" to listOf("b.com"), "statusCodeRange" to "not-a-number"),
            )
        assertNull(parseCaptureRulesFromRemoteConfig(configs))
    }

    @Test
    fun `body without allowlist has empty allowlist`() {
        val configs =
            listOf(
                mapOf(
                    "hosts" to listOf("*"),
                    "requestBody" to mapOf("excludelist" to listOf("secret")),
                ),
            )
        val rules = parseCaptureRulesFromRemoteConfig(configs)
        assertNotNull(rules)
        assertNotNull(rules!![0].requestBody)
        assertTrue(rules[0].requestBody!!.allowlist.isEmpty())
        assertEquals(listOf("secret"), rules[0].requestBody!!.excludelist)
    }

    @Test
    fun `parseStatusCodeRange single values and ranges`() {
        val codes = parseStatusCodeRange("0,200-202,404,500-501")
        assertEquals(listOf(0, 200, 201, 202, 404, 500, 501), codes)
    }
}
