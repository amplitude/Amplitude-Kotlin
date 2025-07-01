package com.amplitude.core.network

import com.amplitude.core.network.NetworkTrackingOptions.CaptureRule
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NetworkTrackingOptionsTest {
    @Test
    fun `capture rules must have non-empty host list`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                NetworkTrackingOptions(
                    captureRules =
                        listOf(
                            CaptureRule(
                                hosts = emptyList(),
                                statusCodeRange = (500..599).toList(),
                            ),
                        ),
                    ignoreAmplitudeRequests = false,
                )
            }
        assertEquals("Capture rules must have a non-empty host list.", exception.message)
    }

    @Test
    fun `capture rules must have non-empty status code range`() {
        val exception =
            assertThrows<IllegalArgumentException> {
                NetworkTrackingOptions(
                    listOf(
                        CaptureRule(
                            hosts = listOf("api.example.com"),
                            statusCodeRange = emptyList(),
                        ),
                    ),
                    ignoreAmplitudeRequests = false,
                )
            }
        assertEquals("Capture rules must have a non-empty status code range.", exception.message)
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
                captureRules =
                    listOf(
                        CaptureRule(
                            hosts = listOf("*"),
                            statusCodeRange = (200..299).toList(),
                        ),
                    ),
                ignoreHosts = emptyList(),
                ignoreAmplitudeRequests = false,
            )

        val matchingHosts =
            listOf(
                "https://api.example.com",
                "https://test.amplitude.com", // one amplitude host
                "https://random-api.com",
            ).urlsToHosts()
        matchingHosts.map { host ->
            assertTrue(options.captureRules.matches(host, 200)) {
                "expected $host to match"
            }
        }
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

        val matchingHosts =
            listOf(
                "https://api.example.com/test",
                "https://another.subdomain.test.com/api",
                "https://exact-match.com/test",
            ).urlsToHosts()
        matchingHosts.forEach { host ->
            assertTrue(options.captureRules.matches(host, 200)) {
                "expected $host to match"
            }
        }

        val nonMatchingHosts =
            listOf(
                "https://notexample.com/test",
                "https://testing.other.com/test",
            ).urlsToHosts()
        nonMatchingHosts.forEach { host ->
            assertFalse(options.captureRules.matches(host, 200))
        }
    }

    @Test
    fun `specific host matching`() {
        val options =
            NetworkTrackingOptions(
                captureRules =
                    listOf(
                        CaptureRule(
                            hosts = listOf("api.example.com"),
                            statusCodeRange = (200..299).toList(),
                        ),
                    ),
                ignoreAmplitudeRequests = true,
            )

        // matching host request
        val matchingHosts =
            listOf(
                "https://api.example.com/test",
                "https://api.example.com/test/123",
                "https://api.example.com/test?param=value",
            ).urlsToHosts()
        matchingHosts.forEach { host ->
            assertTrue(options.captureRules.matches(host, 200))
        }

        // non-matching host requests
        val nonMatchingHosts =
            listOf(
                "https://other.example.com",
                "https://test.amplitude.com", // one amplitude host
                "https://api.different.com",
            ).urlsToHosts()
        nonMatchingHosts.forEach { host ->
            assertFalse(options.captureRules.matches(host, 200))
        }
    }

    @Test
    fun `should ignore hosts with exact and wildcard patterns`() {
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
    fun `multiple capture rules with last matching rule precedence`() {
        val options =
            NetworkTrackingOptions(
                captureRules =
                    listOf(
                        CaptureRule(
                            hosts = listOf("*.example.com"),
                            statusCodeRange = (200..299).toList(),
                        ),
                        CaptureRule(
                            hosts = listOf("api.example.com"),
                            statusCodeRange = (500..599).toList(),
                        ),
                    ),
            )

        assertTrue(options.captureRules.matches("api.example.com", 500))
        assertFalse(options.captureRules.matches("api.example.com", 200))
    }

    @Test
    fun `non-contiguous status code ranges`() {
        val options =
            NetworkTrackingOptions(
                captureRules =
                    listOf(
                        CaptureRule(
                            hosts = listOf("api.example.com"),
                            statusCodeRange = listOf(404, 200, 500),
                        ),
                    ),
            )

        assertTrue(options.captureRules.matches("api.example.com", 200))
        assertTrue(options.captureRules.matches("api.example.com", 404))
        assertTrue(options.captureRules.matches("api.example.com", 500))
        assertFalse(options.captureRules.matches("api.example.com", 300))
    }

    @Test
    fun `host matcher edge cases`() {
        val options =
            NetworkTrackingOptions(
                captureRules =
                    listOf(
                        CaptureRule(
                            hosts =
                                listOf(
                                    "*.sub.*.example.com",
                                    "TEST.EXAMPLE.COM",
                                    "special-chars.example.com",
                                ),
                            statusCodeRange = (200..599).toList(),
                        ),
                    ),
            )

        assertTrue(options.captureRules.matches("test.sub.domain.example.com", 200))
        assertTrue(options.captureRules.matches("test.example.com", 200))
        assertTrue(options.captureRules.matches("special-chars.example.com", 200))
    }

    private fun List<String>.urlsToHosts(): List<String> {
        return map { url -> url.toHttpUrl().host }
    }
}
