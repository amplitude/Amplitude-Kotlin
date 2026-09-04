package com.amplitude.android.streaming.internal.network

import com.amplitude.core.Configuration
import com.amplitude.core.ServerZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

private const val US_DEFAULT_HOST = "https://delayed-events.prod.us-west-2.amplitude.com/2/httpapi"
private const val EU_DEFAULT_HOST = "https://delayed-events.prod.eu-central-1.amplitude.com/2/httpapi"

class DelayedEventsBaseUrlTest {
    @Nested
    inner class HostSelection {
        @Test
        fun `US zone uses the delayed-events US host`() {
            val url =
                DelayedEventsBaseUrl(Configuration(apiKey = "key", serverZone = ServerZone.US))
                    .url("delayed")
            assertEquals("$US_DEFAULT_HOST/delayed", url.toString())
        }

        @Test
        fun `EU zone uses the delayed-events EU host`() {
            val url =
                DelayedEventsBaseUrl(Configuration(apiKey = "key", serverZone = ServerZone.EU))
                    .url("delayed")
            assertEquals("$EU_DEFAULT_HOST/delayed", url.toString())
        }

        @Test
        fun `serverUrl wins over serverZone`() {
            val url =
                DelayedEventsBaseUrl(
                    Configuration(
                        apiKey = "key",
                        serverZone = ServerZone.EU,
                        serverUrl = "https://proxy.example.com/ingest",
                    ),
                ).url("delayed")
            assertEquals("https://proxy.example.com/ingest/delayed", url.toString())
        }

        @Test
        fun `blank serverUrl falls back to serverZone`() {
            val url =
                DelayedEventsBaseUrl(
                    Configuration(
                        apiKey = "key",
                        serverZone = ServerZone.EU,
                        serverUrl = "  ",
                    ),
                ).url("delayed")
            assertEquals("$EU_DEFAULT_HOST/delayed", url.toString())
        }
    }

    @Nested
    inner class Path {
        @Test
        fun `no extra segments keeps the ingest path`() {
            val url = DelayedEventsBaseUrl(Configuration(apiKey = "key")).url()
            assertEquals(US_DEFAULT_HOST, url.toString())
        }

        @Test
        fun `extra segments are trimmed and joined`() {
            val url =
                DelayedEventsBaseUrl(Configuration(apiKey = "key"))
                    .url("/delayed/", "/retry")
            assertEquals("$US_DEFAULT_HOST/delayed/retry", url.toString())
        }

        @Test
        fun `host without a path gets the default ingest path`() {
            val url =
                DelayedEventsBaseUrl(
                    Configuration(apiKey = "key", serverUrl = "https://proxy.example.com"),
                ).url("delayed")
            assertEquals("https://proxy.example.com/2/httpapi/delayed", url.toString())
        }

        @Test
        fun `query and fragment on serverUrl are preserved`() {
            val url =
                DelayedEventsBaseUrl(
                    Configuration(
                        apiKey = "key",
                        serverUrl = "https://proxy.example.com/2/httpapi?x=1#frag",
                    ),
                ).url("delayed")
            assertEquals("https://proxy.example.com/2/httpapi/delayed?x=1#frag", url.toString())
        }
    }
}
