package com.amplitude.android.plugins.privacylayer.models

/**
 * Represents a detected PII entity in text.
 *
 * @property text The actual PII text that was detected
 * @property type The type of PII entity
 * @property startIndex Start position in the original text
 * @property endIndex End position in the original text
 */
data class DetectedPii(
    val text: String,
    val type: EntityType,
    val startIndex: Int,
    val endIndex: Int,
)
