package com.amplitude.android.plugins.privacylayer.models

/**
 * Defines which event fields should be scanned for PII.
 * Based on the updated plan, we only scan user-provided data fields.
 */
enum class ScanField {
    /**
     * Scan event properties - custom key-value pairs (highest risk)
     */
    EVENT_PROPERTIES,

    /**
     * Scan user properties - user profile data (high risk)
     */
    USER_PROPERTIES,

    /**
     * Scan group properties - group data
     */
    GROUP_PROPERTIES,

    /**
     * Scan extra arbitrary data
     */
    EXTRA,
}
