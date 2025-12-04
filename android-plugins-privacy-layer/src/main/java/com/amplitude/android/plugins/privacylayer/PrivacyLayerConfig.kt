package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.EntityType
import com.amplitude.android.plugins.privacylayer.models.RedactionStrategy
import com.amplitude.android.plugins.privacylayer.models.ScanField

/**
 * Configuration for the Privacy Layer Plugin.
 *
 * @property entityTypes Which entity types to detect and redact
 * @property scanFields Which event fields to scan for PII
 * @property redactionStrategy How to redact detected PII
 * @property whitelist Regex patterns for values that should NOT be redacted
 * @property customPatterns Additional regex patterns for custom PII types (pattern name to regex)
 * @property maxTextLength Maximum text length to analyze (prevents OOM on very large strings)
 * @property useMlKit Enable/disable MLKit (Phase 2 - currently unused)
 */
data class PrivacyLayerConfig(
    val entityTypes: Set<EntityType> =
        setOf(
            EntityType.EMAIL,
            EntityType.PHONE_NUMBER,
            EntityType.ADDRESS,
            EntityType.PAYMENT_CARD,
            EntityType.IBAN,
        ),
    val scanFields: Set<ScanField> =
        setOf(
            ScanField.EVENT_PROPERTIES,
            ScanField.USER_PROPERTIES,
            ScanField.GROUP_PROPERTIES,
            ScanField.EXTRA,
        ),
    val redactionStrategy: RedactionStrategy = RedactionStrategy.TYPE_SPECIFIC,
    val whitelist: List<Regex> = emptyList(),
    val customPatterns: Map<String, Regex> = emptyMap(),
    val maxTextLength: Int = 10_000,
    // Phase 2: Will enable MLKit
    val useMlKit: Boolean = false,
)
