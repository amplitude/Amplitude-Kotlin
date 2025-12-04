package com.amplitude.android.plugins.privacylayer

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventScannerTest {
    @Test
    fun `scanText should return empty list in Phase 1`() {
        val config = PrivacyLayerConfig()
        val scanner = EventScanner(config)

        val result = scanner.scanText("test@example.com")

        // Phase 1: No actual detection yet
        assertTrue(result.isEmpty())
    }

    @Test
    fun `scanText should handle empty string`() {
        val config = PrivacyLayerConfig()
        val scanner = EventScanner(config)

        val result = scanner.scanText("")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `scanText should handle various PII patterns in Phase 1`() {
        val config = PrivacyLayerConfig()
        val scanner = EventScanner(config)

        val texts =
            listOf(
                "john@example.com",
                "(555) 123-4567",
                "4111 1111 1111 1111",
                "123 Main St, Cambridge MA",
            )

        for (text in texts) {
            val result = scanner.scanText(text)
            // Phase 1: Should return empty (detection added in Phase 2)
            assertTrue(result.isEmpty(), "Expected empty for: $text")
        }
    }
}
