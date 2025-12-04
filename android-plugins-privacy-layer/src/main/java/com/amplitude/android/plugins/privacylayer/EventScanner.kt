package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.DetectedPii

/**
 * Scans text for PII entities.
 *
 * Phase 1: Basic structure with no actual detection
 * Phase 2: Will add MLKit integration and regex patterns
 *
 * @property config Configuration for scanning
 */
class EventScanner(
    private val config: PrivacyLayerConfig,
) {
    /**
     * Scans text for PII entities.
     *
     * Phase 1: Returns empty list (no detection yet)
     * Phase 2: Will implement actual detection using MLKit and regex
     *
     * @param text The text to scan
     * @return List of detected PII entities
     */
    fun scanText(text: String): List<DetectedPii> {
        // Phase 1: Basic structure, no actual detection
        // Phase 2: Will add:
        // 1. MLKit entity extraction (if enabled)
        // 2. Custom regex patterns
        // 3. Whitelist filtering

        // For now, return empty list
        return emptyList()
    }

    /**
     * Checks if a detected PII value matches any whitelist patterns.
     * Whitelisted values should NOT be redacted.
     *
     * @param text The PII text to check
     * @return true if the value should be whitelisted (not redacted)
     */
    private fun isWhitelisted(text: String): Boolean {
        return config.whitelist.any { pattern ->
            pattern.matches(text)
        }
    }
}
