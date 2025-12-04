package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.DetectedPii
import com.amplitude.android.plugins.privacylayer.models.EntityType
import com.amplitude.android.plugins.privacylayer.models.RedactionStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PiiRedactorTest {
    @Test
    fun `redact with TYPE_SPECIFIC strategy should replace with token`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.TYPE_SPECIFIC)
        val redactor = PiiRedactor(config)

        val text = "Contact me at john@example.com"
        val detectedPii =
            listOf(
                DetectedPii(
                    text = "john@example.com",
                    type = EntityType.EMAIL,
                    startIndex = 14,
                    endIndex = 30,
                ),
            )

        val result = redactor.redact(text, detectedPii)

        assertEquals("Contact me at [REDACTED_EMAIL]", result)
    }

    @Test
    fun `redact with TYPE_SPECIFIC should handle multiple entities`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.TYPE_SPECIFIC)
        val redactor = PiiRedactor(config)

        val text = "Email: user@test.com, Phone: 555-1234"
        val detectedPii =
            listOf(
                DetectedPii("user@test.com", EntityType.EMAIL, 7, 20),
                DetectedPii("555-1234", EntityType.PHONE_NUMBER, 29, 37),
            )

        val result = redactor.redact(text, detectedPii)

        assertEquals("Email: [REDACTED_EMAIL], Phone: [REDACTED_PHONE]", result)
    }

    @Test
    fun `redact with HASH strategy should replace with hash`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.HASH)
        val redactor = PiiRedactor(config)

        val text = "Email: john@example.com"
        val detectedPii =
            listOf(
                DetectedPii("john@example.com", EntityType.EMAIL, 7, 23),
            )

        val result = redactor.redact(text, detectedPii) as String

        assertTrue(result.startsWith("Email: hash:"))
        assertTrue(result.contains("hash:"))
        assertEquals(20, result.length) // "Email: hash:" (12) + 8 char hash (8) = 20
    }

    @Test
    fun `redact with REMOVE strategy should return marker`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.REMOVE)
        val redactor = PiiRedactor(config)

        val text = "john@example.com"
        val detectedPii =
            listOf(
                DetectedPii("john@example.com", EntityType.EMAIL, 0, 16),
            )

        val result = redactor.redact(text, detectedPii)

        assertEquals(PiiRedactor.REMOVED_MARKER, result)
    }

    @Test
    fun `redact should return original text when no PII detected`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.TYPE_SPECIFIC)
        val redactor = PiiRedactor(config)

        val text = "No PII here"
        val detectedPii = emptyList<DetectedPii>()

        val result = redactor.redact(text, detectedPii)

        assertEquals("No PII here", result)
    }

    @Test
    fun `redact should handle all entity types`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.TYPE_SPECIFIC)
        val redactor = PiiRedactor(config)

        val entityTypes =
            listOf(
                EntityType.EMAIL to "[REDACTED_EMAIL]",
                EntityType.PHONE_NUMBER to "[REDACTED_PHONE]",
                EntityType.ADDRESS to "[REDACTED_ADDRESS]",
                EntityType.PAYMENT_CARD to "[REDACTED_CARD]",
                EntityType.IBAN to "[REDACTED_IBAN]",
                EntityType.URL to "[REDACTED_URL]",
                EntityType.DATE_TIME to "[REDACTED_DATE]",
                EntityType.FLIGHT_NUMBER to "[REDACTED_FLIGHT]",
                EntityType.ISBN to "[REDACTED_ISBN]",
                EntityType.MONEY to "[REDACTED_MONEY]",
                EntityType.TRACKING_NUMBER to "[REDACTED_TRACKING]",
            )

        for ((type, expectedToken) in entityTypes) {
            val text = "Value: XXXXX"
            val detectedPii =
                listOf(
                    DetectedPii("XXXXX", type, 7, 12),
                )

            val result = redactor.redact(text, detectedPii)

            assertEquals("Value: $expectedToken", result, "Failed for $type")
        }
    }

    @Test
    fun `hash should be consistent for same input`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.HASH)
        val redactor = PiiRedactor(config)

        val text = "test@example.com"
        val detectedPii =
            listOf(
                DetectedPii(text, EntityType.EMAIL, 0, text.length),
            )

        val result1 = redactor.redact(text, detectedPii)
        val result2 = redactor.redact(text, detectedPii)

        assertEquals(result1, result2)
    }

    @Test
    fun `redact should handle overlapping entities correctly`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.TYPE_SPECIFIC)
        val redactor = PiiRedactor(config)

        // Email containing URL
        val text = "Contact: user@example.com"
        val detectedPii =
            listOf(
                DetectedPii("user@example.com", EntityType.EMAIL, 9, 25),
            )

        val result = redactor.redact(text, detectedPii)

        assertEquals("Contact: [REDACTED_EMAIL]", result)
    }

    @Test
    fun `redact should handle entities at start and end of text`() {
        val config = PrivacyLayerConfig(redactionStrategy = RedactionStrategy.TYPE_SPECIFIC)
        val redactor = PiiRedactor(config)

        // Entity at start
        val text1 = "user@test.com is the email"
        val detectedPii1 =
            listOf(
                DetectedPii("user@test.com", EntityType.EMAIL, 0, 13),
            )
        assertEquals("[REDACTED_EMAIL] is the email", redactor.redact(text1, detectedPii1))

        // Entity at end
        val text2 = "Email is user@test.com"
        val detectedPii2 =
            listOf(
                DetectedPii("user@test.com", EntityType.EMAIL, 9, 22),
            )
        assertEquals("Email is [REDACTED_EMAIL]", redactor.redact(text2, detectedPii2))

        // Entire text is entity
        val text3 = "user@test.com"
        val detectedPii3 =
            listOf(
                DetectedPii("user@test.com", EntityType.EMAIL, 0, 13),
            )
        assertEquals("[REDACTED_EMAIL]", redactor.redact(text3, detectedPii3))
    }
}
