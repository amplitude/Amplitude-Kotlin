package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.DetectedPii
import com.amplitude.android.plugins.privacylayer.models.EntityType.EMAIL
import com.amplitude.android.plugins.privacylayer.models.EntityType.IBAN
import com.amplitude.android.plugins.privacylayer.models.EntityType.PAYMENT_CARD
import com.amplitude.android.plugins.privacylayer.models.EntityType.PHONE_NUMBER
import com.amplitude.android.plugins.privacylayer.models.EntityType.URL

/**
 * Regex-based PII detector.
 * Provides fallback detection when MLKit is unavailable or for custom patterns.
 *
 * @property config Configuration for detection
 */
class RegexPiiDetector(
    private val config: PrivacyLayerConfig,
) {
    // Common regex patterns for PII detection
    private val patterns =
        mapOf(
            EMAIL to
                Regex(
                    pattern = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}",
                    option = RegexOption.IGNORE_CASE,
                ),
            PHONE_NUMBER to
                Regex(
                    // Matches various phone formats: (555) 123-4567, 555-123-4567, 555.123.4567, +1-555-123-4567
                    pattern = "(?:\\+\\d{1,3}[-.\\s]?)?(?:\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}",
                ),
            PAYMENT_CARD to
                Regex(
                    // Matches credit card formats with optional spaces or dashes
                    pattern = "\\b(?:\\d{4}[\\s-]?){3}\\d{4}\\b",
                ),
            URL to
                Regex(
                    pattern = "https?://[\\w.-]+(?:\\.[\\w.-]+)+[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]*",
                    option = RegexOption.IGNORE_CASE,
                ),
            IBAN to
                Regex(
                    // Simplified IBAN pattern (2 letters + 2 digits + up to 30 alphanumeric)
                    pattern = "\\b[A-Z]{2}\\d{2}[A-Z0-9]{1,30}\\b",
                ),
        )

    /**
     * Detect PII entities in the given text using regex patterns.
     *
     * @param text The text to analyze
     * @return List of detected PII entities
     */
    fun detectPii(text: String): List<DetectedPii> {
        val results = mutableListOf<DetectedPii>()

        // Check built-in patterns
        for ((entityType, pattern) in patterns) {
            // Skip if not in configured entity types
            if (entityType !in config.entityTypes) {
                continue
            }

            val matches = pattern.findAll(text)
            for (match in matches) {
                results.add(
                    DetectedPii(
                        text = match.value,
                        type = entityType,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                    ),
                )
            }
        }

        // Check custom patterns
        for ((_, pattern) in config.customPatterns) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                // Custom patterns are treated as EMAIL type for now
                // Could be extended to support custom EntityType mapping
                // Default type for custom patterns
                results.add(
                    DetectedPii(
                        text = match.value,
                        type = EMAIL,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                    ),
                )
            }
        }

        return results
    }

    /**
     * Check if detected PII should be filtered out based on whitelist.
     */
    fun isWhitelisted(text: String): Boolean {
        return config.whitelist.any { pattern ->
            pattern.matches(text)
        }
    }
}
