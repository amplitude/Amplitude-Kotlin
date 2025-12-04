package com.amplitude.android.plugins.privacylayer.models

/**
 * Types of PII entities that can be detected.
 * These map to MLKit Entity Extraction types (Phase 2).
 */
enum class EntityType(val displayName: String) {
    /**
     * Email addresses (e.g., user@example.com)
     */
    EMAIL("Email"),

    /**
     * Phone numbers (e.g., (555) 123-4567)
     */
    PHONE_NUMBER("Phone Number"),

    /**
     * Physical addresses (e.g., 350 third street, Cambridge MA)
     */
    ADDRESS("Address"),

    /**
     * Payment/credit card numbers (e.g., 4111 1111 1111 1111)
     */
    PAYMENT_CARD("Payment Card"),

    /**
     * International Bank Account Numbers (e.g., CH52 0483 0000 0000 0000 9)
     */
    IBAN("IBAN"),

    /**
     * URLs (e.g., www.google.com - might contain PII in query params)
     */
    URL("URL"),

    /**
     * Date-time information (optional, usually not PII but can be in some contexts)
     */
    DATE_TIME("Date/Time"),

    /**
     * Flight numbers (usually not PII but included for completeness)
     */
    FLIGHT_NUMBER("Flight Number"),

    /**
     * ISBN book numbers (usually not PII)
     */
    ISBN("ISBN"),

    /**
     * Money/currency amounts (usually not PII)
     */
    MONEY("Money"),

    /**
     * Package tracking numbers (usually not PII)
     */
    TRACKING_NUMBER("Tracking Number"),
}
