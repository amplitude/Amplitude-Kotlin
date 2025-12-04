package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.DetectedPii
import com.amplitude.android.plugins.privacylayer.models.EntityType
import com.amplitude.android.plugins.privacylayer.models.RedactionStrategy
import java.security.MessageDigest

/**
 * Handles redaction of detected PII based on the configured strategy.
 *
 * @property config Configuration for redaction
 */
class PiiRedactor(
    private val config: PrivacyLayerConfig,
) {
    /**
     * Redacts PII from text based on the configured strategy.
     *
     * @param originalText The original text containing PII
     * @param detectedPii List of detected PII entities
     * @return The redacted text, or a special marker for REMOVE strategy
     */
    fun redact(
        originalText: String,
        detectedPii: List<DetectedPii>,
    ): Any {
        if (detectedPii.isEmpty()) {
            return originalText
        }

        return when (config.redactionStrategy) {
            RedactionStrategy.TYPE_SPECIFIC -> redactTypeSpecific(originalText, detectedPii)
            RedactionStrategy.HASH -> redactWithHash(originalText, detectedPii)
            RedactionStrategy.REMOVE -> REMOVED_MARKER
        }
    }

    /**
     * Redacts PII by replacing with type-specific tokens.
     * Example: "john@example.com" → "[REDACTED_EMAIL]"
     */
    private fun redactTypeSpecific(
        text: String,
        detectedPii: List<DetectedPii>,
    ): String {
        var result = text

        // Sort by position (descending) to avoid index shifting during replacement
        val sortedPii = detectedPii.sortedByDescending { it.startIndex }

        for (pii in sortedPii) {
            val token = getRedactionToken(pii.type)
            result = result.replaceRange(pii.startIndex, pii.endIndex, token)
        }

        return result
    }

    /**
     * Redacts PII by replacing with hash of the value.
     * Example: "john@example.com" → "hash:a3f5d9e2..."
     */
    private fun redactWithHash(
        text: String,
        detectedPii: List<DetectedPii>,
    ): String {
        var result = text

        // Sort by position (descending) to avoid index shifting during replacement
        val sortedPii = detectedPii.sortedByDescending { it.startIndex }

        for (pii in sortedPii) {
            val hash = hashString(pii.text)
            val token = "hash:$hash"
            result = result.replaceRange(pii.startIndex, pii.endIndex, token)
        }

        return result
    }

    /**
     * Gets the redaction token for a given entity type.
     */
    private fun getRedactionToken(type: EntityType): String {
        return when (type) {
            EntityType.EMAIL -> "[REDACTED_EMAIL]"
            EntityType.PHONE_NUMBER -> "[REDACTED_PHONE]"
            EntityType.ADDRESS -> "[REDACTED_ADDRESS]"
            EntityType.PAYMENT_CARD -> "[REDACTED_CARD]"
            EntityType.IBAN -> "[REDACTED_IBAN]"
            EntityType.URL -> "[REDACTED_URL]"
            EntityType.DATE_TIME -> "[REDACTED_DATE]"
            EntityType.FLIGHT_NUMBER -> "[REDACTED_FLIGHT]"
            EntityType.ISBN -> "[REDACTED_ISBN]"
            EntityType.MONEY -> "[REDACTED_MONEY]"
            EntityType.TRACKING_NUMBER -> "[REDACTED_TRACKING]"
        }
    }

    /**
     * Creates a hash of the string for consistent redaction.
     * Uses SHA-256 and returns first 8 characters.
     */
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }

    companion object {
        /**
         * Special marker returned for REMOVE strategy.
         */
        const val REMOVED_MARKER = "__REMOVED__"
    }
}
