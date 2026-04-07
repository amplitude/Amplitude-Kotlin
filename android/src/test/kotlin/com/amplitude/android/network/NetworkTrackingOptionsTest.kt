package com.amplitude.android.network

import com.amplitude.android.network.NetworkTrackingOptions.CaptureBody
import com.amplitude.android.network.NetworkTrackingOptions.CaptureHeader
import com.amplitude.android.network.NetworkTrackingOptions.CaptureRule
import com.amplitude.android.network.NetworkTrackingOptions.URLPattern
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NetworkTrackingOptionsTest {
    private fun List<CaptureRule>.matches(
        host: String,
        responseCode: Int,
    ): Boolean = findMatchingRule(host, "https://$host/test", "GET", responseCode) != null

    @Test
    fun `capture rules must have non-empty host or url list`() {
        assertThrows<IllegalArgumentException> {
            NetworkTrackingOptions(
                captureRules = listOf(CaptureRule(hosts = emptyList(), statusCodeRange = (500..599).toList())),
                ignoreAmplitudeRequests = false,
            )
        }
    }

    @Test
    fun `capture rules must have non-empty status code range`() {
        assertThrows<IllegalArgumentException> {
            NetworkTrackingOptions(
                listOf(CaptureRule(hosts = listOf("api.example.com"), statusCodeRange = emptyList())),
                ignoreAmplitudeRequests = false,
            )
        }
    }

    @Test
    fun `default capture options`() {
        val options = NetworkTrackingOptions.DEFAULT
        assertEquals(1, options.captureRules.size)
        with(options.captureRules.first()) {
            assertEquals("*", hosts[0])
            assertEquals((500..599).toList(), statusCodeRange)
        }
        assertTrue(options.ignoreHosts.isEmpty())
        assertTrue(options.ignoreAmplitudeRequests)
    }

    @Test
    fun `basic wildcard host matching`() {
        val options =
            NetworkTrackingOptions(
                captureRules = listOf(CaptureRule(hosts = listOf("*"), statusCodeRange = (200..299).toList())),
                ignoreAmplitudeRequests = false,
            )
        listOf("https://api.example.com", "https://test.amplitude.com", "https://random-api.com")
            .urlsToHosts()
            .forEach { assertTrue(options.captureRules.matches(it, 200)) { "expected $it to match" } }
    }

    @Test
    fun `domain wildcard host matching`() {
        val options =
            NetworkTrackingOptions(
                captureRules =
                    listOf(
                        CaptureRule(
                            hosts = listOf("*.example.com", "*.subdomain.test.com", "exact-match.com"),
                            statusCodeRange = (200..299).toList(),
                        ),
                    ),
                ignoreAmplitudeRequests = false,
            )
        listOf("https://api.example.com/test", "https://another.subdomain.test.com/api", "https://exact-match.com/test")
            .urlsToHosts().forEach { assertTrue(options.captureRules.matches(it, 200)) }
        listOf("https://notexample.com/test", "https://testing.other.com/test")
            .urlsToHosts().forEach { assertFalse(options.captureRules.matches(it, 200)) }
    }

    @Test
    fun `specific host matching`() {
        val options =
            NetworkTrackingOptions(
                captureRules = listOf(CaptureRule(hosts = listOf("api.example.com"), statusCodeRange = (200..299).toList())),
                ignoreAmplitudeRequests = true,
            )
        listOf("https://api.example.com/test", "https://api.example.com/test/123").urlsToHosts()
            .forEach { assertTrue(options.captureRules.matches(it, 200)) }
        listOf("https://other.example.com", "https://api.different.com").urlsToHosts()
            .forEach { assertFalse(options.captureRules.matches(it, 200)) }
    }

    @Test
    fun `should ignore hosts`() {
        val options =
            NetworkTrackingOptions(
                captureRules = listOf(CaptureRule(hosts = listOf("*"))),
                ignoreHosts = listOf("exact.ignore.com", "*.wildcard.ignore.com"),
            )
        assertTrue(options.shouldIgnore("exact.ignore.com"))
        assertTrue(options.shouldIgnore("test.wildcard.ignore.com"))
        assertFalse(options.shouldIgnore("not.ignored.com"))
    }

    @Test
    fun `should ignore amplitude hosts`() {
        val options = NetworkTrackingOptions(captureRules = listOf(CaptureRule(hosts = listOf("*"))), ignoreAmplitudeRequests = true)
        assertTrue(options.shouldIgnore("amplitude.com"))
        assertTrue(options.shouldIgnore("api2.amplitude.com"))
        assertTrue(options.shouldIgnore("api.eu.amplitude.com"))
        assertFalse(options.shouldIgnore("notamplitude.com"))
    }

    @Test
    fun `should not ignore amplitude hosts when disabled`() {
        val options = NetworkTrackingOptions(captureRules = listOf(CaptureRule(hosts = listOf("*"))), ignoreAmplitudeRequests = false)
        assertFalse(options.shouldIgnore("api2.amplitude.com"))
    }

    @Test
    fun `multiple capture rules with last matching rule precedence`() {
        val options =
            NetworkTrackingOptions(
                captureRules =
                    listOf(
                        CaptureRule(hosts = listOf("*.example.com"), statusCodeRange = (200..299).toList()),
                        CaptureRule(hosts = listOf("api.example.com"), statusCodeRange = (500..599).toList()),
                    ),
            )
        assertTrue(options.captureRules.matches("api.example.com", 500))
        assertFalse(options.captureRules.matches("api.example.com", 200))
    }

    @Test
    fun `non-contiguous status code ranges`() {
        val options =
            NetworkTrackingOptions(
                captureRules = listOf(CaptureRule(hosts = listOf("api.example.com"), statusCodeRange = listOf(404, 200, 500))),
            )
        assertTrue(options.captureRules.matches("api.example.com", 200))
        assertTrue(options.captureRules.matches("api.example.com", 404))
        assertFalse(options.captureRules.matches("api.example.com", 300))
    }

    @Test
    fun `host matcher edge cases`() {
        val options =
            NetworkTrackingOptions(
                captureRules =
                    listOf(
                        CaptureRule(
                            hosts = listOf("*.sub.*.example.com", "TEST.EXAMPLE.COM", "special-chars.example.com"),
                            statusCodeRange = (200..599).toList(),
                        ),
                    ),
            )
        assertTrue(options.captureRules.matches("test.sub.domain.example.com", 200))
        assertTrue(options.captureRules.matches("test.example.com", 200))
        assertTrue(options.captureRules.matches("special-chars.example.com", 200))
    }

    @Test fun `URL pattern exact matching`() {
        val rule = CaptureRule(urls = listOf(URLPattern.Exact("https://api.example.com/v1/users")), statusCodeRange = (200..299).toList())
        assertTrue(rule.matchesRequest("api.example.com", "https://api.example.com/v1/users", "GET"))
        assertFalse(rule.matchesRequest("api.example.com", "https://api.example.com/v1/products", "GET"))
    }

    @Test fun `URL pattern regex matching`() {
        val rule =
            CaptureRule(urls = listOf(URLPattern.Regex("https://api\\.example\\.com/v[0-9]+/.*")), statusCodeRange = (200..299).toList())
        assertTrue(rule.matchesRequest("api.example.com", "https://api.example.com/v1/users", "GET"))
        assertFalse(rule.matchesRequest("api.example.com", "https://other.com/v1/users", "GET"))
    }

    @Test fun `invalid regex rejected at construction`() {
        assertThrows<java.util.regex.PatternSyntaxException> {
            CaptureRule(urls = listOf(URLPattern.Regex("[invalid")), statusCodeRange = (200..299).toList())
        }
    }

    @Test fun `method matching`() {
        val rule =
            CaptureRule(
                urls = listOf(URLPattern.Exact("https://e.com/t")),
                methods = listOf("GET", "POST"),
                statusCodeRange = (200..299).toList(),
            )
        assertTrue(rule.matchesRequest("e.com", "https://e.com/t", "GET"))
        assertTrue(rule.matchesRequest("e.com", "https://e.com/t", "post"))
        assertFalse(rule.matchesRequest("e.com", "https://e.com/t", "DELETE"))
    }

    @Test fun `empty methods matches all`() {
        val rule =
            CaptureRule(
                urls = listOf(URLPattern.Exact("https://e.com/t")),
                statusCodeRange = (200..299).toList(),
            )
        assertTrue(rule.matchesRequest("e.com", "https://e.com/t", "DELETE"))
    }

    @Test fun `findMatchingRule returns rule with config`() {
        val ch = CaptureHeader(allowlist = listOf("ct"), captureSafeHeaders = false)
        val cb = CaptureBody(allowlist = listOf("user/name"))
        val rules =
            listOf(
                CaptureRule(
                    urls = listOf(URLPattern.Regex(".*e\\.com.*")),
                    statusCodeRange = (200..299).toList(),
                    requestHeaders = ch,
                    requestBody = cb,
                ),
            )
        val m = rules.findMatchingRule("e.com", "https://e.com/t", "GET", 200)
        assertNotNull(m)
        assertEquals(ch, m!!.requestHeaders)
        assertEquals(cb, m.requestBody)
    }

    // --- Header filtering ---

    @Test fun `filter headers with safe headers`() {
        val ch = CaptureHeader(captureSafeHeaders = true)
        val headers = headersOf("Content-Type", "json", "Authorization", "Bearer x", "Cache-Control", "no-cache")
        val r = ch.filterHeaders(headers)
        assertNotNull(r)
        assertEquals("json", r!!["Content-Type"])
        assertEquals("no-cache", r["Cache-Control"])
        assertNull(r["Authorization"])
    }

    @Test fun `filter headers blocked even if in allowlist`() {
        val ch = CaptureHeader(allowlist = listOf("authorization", "cookie"), captureSafeHeaders = false)
        assertNull(ch.filterHeaders(headersOf("Authorization", "x", "Cookie", "y")))
    }

    @Test fun `filter headers case insensitive`() {
        val ch = CaptureHeader(allowlist = listOf("X-CUSTOM"), captureSafeHeaders = false)
        val r = ch.filterHeaders(headersOf("x-custom", "v"))
        assertEquals("v", r!!["x-custom"])
    }

    @Test fun `filter headers preserves multi-valued headers`() {
        val ch = CaptureHeader(allowlist = listOf("x-multi"), captureSafeHeaders = false)
        val headers = headersOf("X-Multi", "a", "X-Multi", "b", "X-Multi", "c")
        val r = ch.filterHeaders(headers)
        assertNotNull(r)
        assertEquals(listOf("a", "b", "c"), r!!["X-Multi"])
    }

    @Test fun `filter headers single-valued header is String not List`() {
        val ch = CaptureHeader(allowlist = listOf("x-single"), captureSafeHeaders = false)
        val r = ch.filterHeaders(headersOf("X-Single", "only"))
        assertNotNull(r)
        assertTrue(r!!["X-Single"] is String)
        assertEquals("only", r["X-Single"])
    }

    // --- Body filtering ---

    @Test fun `filter body bytes JSON object`() {
        val cb = CaptureBody(allowlist = listOf("user/name"))
        val r = cb.filterBodyBytes("""{"user":{"name":"John","password":"secret"}}""".toByteArray())
        assertNotNull(r)
        assertTrue(r!!.contains("John"))
        assertFalse(r.contains("secret"))
    }

    @Test fun `filter body bytes non-JSON returns null`() {
        assertNull(CaptureBody(allowlist = listOf("k")).filterBodyBytes("not json".toByteArray()))
    }

    @Test fun `filter body bytes null returns null`() {
        assertNull(CaptureBody(allowlist = listOf("k")).filterBodyBytes(null))
    }

    @Test fun `filter body bytes with blocklist`() {
        val cb = CaptureBody(allowlist = listOf("user/**"), excludelist = listOf("user/password"))
        val r = cb.filterBodyBytes("""{"user":{"name":"John","password":"secret","email":"j@e.com"}}""".toByteArray())
        assertNotNull(r)
        assertTrue(r!!.contains("John"))
        assertTrue(r.contains("j@e.com"))
        assertFalse(r.contains("secret"))
    }

    private fun List<String>.urlsToHosts(): List<String> = map { it.toHttpUrl().host }
}
