package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.DetectedPii

/**
 * Scans text for PII entities using MLKit and regex patterns.
 *
 * If MLKit is enabled, it will be used as the primary detector.
 * Regex detection is always run as a fallback and supplement.
 *
 * @property config Configuration for scanning
 * @property regexDetector Regex-based detector (always used as fallback)
 * @property mlKitDetector MLKit-based detector (optional, used when available)
 */
class EventScanner(
    private val config: PrivacyLayerConfig,
    private val regexDetector: RegexPiiDetector = RegexPiiDetector(config),
    private val mlKitDetector: MlKitPiiDetector? = null,
) {
    /**
     * Scans text for PII entities using MLKit and regex patterns.
     *
     * If MLKit is enabled and available, it will be used first.
     * Regex detection is always run as a supplement.
     * Results are deduplicated and filtered by whitelist.
     *
     * @param text The text to scan
     * @return List of detected PII entities
     */
    suspend fun scanText(text: String): List<DetectedPii> {
        val results = mutableListOf<DetectedPii>()

        // Try MLKit first if enabled and available
        if (config.useMlKit && mlKitDetector != null) {
            results.addAll(mlKitDetector.detectPii(text))
        }

        // Always run regex (fallback + supplement)
        results.addAll(regexDetector.detectPii(text))

        // Deduplicate overlapping entities (prefer longer matches)
        val deduplicated = deduplicateEntities(results)

        // Filter out whitelisted values
        return deduplicated.filter { !isWhitelisted(it.text) }
    }

    /**
     * Deduplicate overlapping entities.
     * When multiple entities overlap, keep the longest match.
     */
    private fun deduplicateEntities(entities: List<DetectedPii>): List<DetectedPii> {
        if (entities.isEmpty()) return emptyList()

        // Sort by start position, then by length (descending)
        val sorted =
            entities.sortedWith(
                compareBy<DetectedPii> { it.startIndex }
                    .thenByDescending { it.endIndex - it.startIndex },
            )

        val result = mutableListOf<DetectedPii>()
        var lastEnd = -1

        for (entity in sorted) {
            // Skip if this entity overlaps with the previous one
            if (entity.startIndex < lastEnd) {
                continue
            }

            result.add(entity)
            lastEnd = entity.endIndex
        }

        return result
    }

    /**
     * Checks if a detected PII value matches any whitelist patterns.
     * Whitelisted values should NOT be redacted.
     *
     * @param text The PII text to check
     * @return true if the value should be whitelisted (not redacted)
     */
    private fun isWhitelisted(text: String): Boolean {
        return regexDetector.isWhitelisted(text)
    }
}
