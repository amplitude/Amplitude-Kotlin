package com.amplitude.android.plugins.privacylayer.models

/**
 * Defines how detected PII should be redacted.
 */
enum class RedactionStrategy {
    /**
     * Replace with type-specific tokens (e.g., [REDACTED_EMAIL], [REDACTED_PHONE])
     * Recommended: Clear what was redacted, helps debugging
     */
    TYPE_SPECIFIC,

    /**
     * Replace with hash of the value (e.g., hash:a3f5d9e2...)
     * Provides consistent identifier for same value, allows deduplication
     */
    HASH,

    /**
     * Remove the property completely
     * Most secure but loses data completely
     */
    REMOVE,
}
