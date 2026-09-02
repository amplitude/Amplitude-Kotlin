package com.amplitude.android.streaming.internal.network

import com.amplitude.core.Configuration
import com.amplitude.core.Constants
import com.amplitude.core.ServerZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AmplitudeBaseUrlTest {
    @Nested
    inner class HostSelection {
        @Test
        fun `US zone uses the default ingest host`() {
            val url =
                AmplitudeBaseUrl(Configuration(apiKey = "key", serverZone = ServerZone.US))
                    .url("delayed")
            assertEquals("https://api2.amplitude.com/2/httpapi/delayed", url.toString())
        }

        @Test
        fun `EU zone uses the EU ingest host`() {
            val url =
                AmplitudeBaseUrl(Configuration(apiKey = "key", serverZone = ServerZone.EU))
                    .url("delayed")
            assertEquals("https://api.eu.amplitude.com/2/httpapi/delayed", url.toString())
        }

        @Test
        fun `serverUrl wins over serverZone`() {
            val url =
                AmplitudeBaseUrl(
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
                AmplitudeBaseUrl(
                    Configuration(
                        apiKey = "key",
                        serverZone = ServerZone.EU,
                        serverUrl = "  ",
                    ),
                ).url("delayed")
            assertEquals("${Constants.EU_DEFAULT_API_HOST}/delayed", url.toString())
        }
    }

    @Nested
    inner class Path {
        @Test
        fun `no extra segments keeps the ingest path`() {
            val url = AmplitudeBaseUrl(Configuration(apiKey = "key")).url()
            assertEquals(Constants.DEFAULT_API_HOST, url.toString())
        }

        @Test
        fun `extra segments are trimmed and joined`() {
            val url =
                AmplitudeBaseUrl(Configuration(apiKey = "key"))
                    .url("/delayed/", "/retry")
            assertEquals("https://api2.amplitude.com/2/httpapi/delayed/retry", url.toString())
        }

        @Test
        fun `host without a path gets the default ingest path`() {
            val url =
                AmplitudeBaseUrl(
                    Configuration(apiKey = "key", serverUrl = "https://proxy.example.com"),
                ).url("delayed")
            assertEquals("https://proxy.example.com/2/httpapi/delayed", url.toString())
        }

        @Test
        fun `query and fragment on serverUrl are preserved`() {
            val url =
                AmplitudeBaseUrl(
                    Configuration(
                        apiKey = "key",
                        serverUrl = "https://proxy.example.com/2/httpapi?x=1#frag",
                    ),
                ).url("delayed")
            assertEquals("https://proxy.example.com/2/httpapi/delayed?x=1#frag", url.toString())
        }
    }
}
