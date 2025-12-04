package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.EntityType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventScannerTest {
    @Test
    fun `scanText should detect email addresses`() =
        runBlocking {
            // Disable MLKit to test regex-only detection
            val config = PrivacyLayerConfig(useMlKit = false)
            val scanner = EventScanner(config)

            val result = scanner.scanText("test@example.com")

            assertEquals(1, result.size)
            assertEquals(EntityType.EMAIL, result[0].type)
            assertEquals("test@example.com", result[0].text)
        }

    @Test
    fun `scanText should handle empty string`() =
        runBlocking {
            val config = PrivacyLayerConfig(useMlKit = false)
            val scanner = EventScanner(config)

            val result = scanner.scanText("")

            assertTrue(result.isEmpty())
        }

    @Test
    fun `scanText should detect various PII patterns`() =
        runBlocking {
            // Disable MLKit to test regex-only detection
            val config = PrivacyLayerConfig(useMlKit = false)
            val scanner = EventScanner(config)

            val emailResult = scanner.scanText("john@example.com")
            assertEquals(1, emailResult.size)
            assertEquals(EntityType.EMAIL, emailResult[0].type)

            val phoneResult = scanner.scanText("(555) 123-4567")
            assertEquals(1, phoneResult.size)
            assertEquals(EntityType.PHONE_NUMBER, phoneResult[0].type)

            val cardResult = scanner.scanText("4111 1111 1111 1111")
            assertEquals(1, cardResult.size)
            assertEquals(EntityType.PAYMENT_CARD, cardResult[0].type)
        }
}
