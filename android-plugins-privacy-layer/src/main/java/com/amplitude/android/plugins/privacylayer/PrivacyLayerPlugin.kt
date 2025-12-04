package com.amplitude.android.plugins.privacylayer

import com.amplitude.android.plugins.privacylayer.models.ScanField
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin

/**
 * Privacy Layer Plugin for Amplitude Android SDK.
 *
 * This plugin automatically detects and redacts PII (Personally Identifiable Information)
 * from events before they are sent to Amplitude servers.
 *
 * It runs as a Before plugin, intercepting events early in the pipeline.
 *
 * Usage:
 * ```
 * val amplitude = Amplitude(apiKey, context)
 * amplitude.add(
 *     PrivacyLayerPlugin(
 *         config = PrivacyLayerConfig(
 *             entityTypes = setOf(EntityType.EMAIL, EntityType.PHONE_NUMBER),
 *             redactionStrategy = RedactionStrategy.TYPE_SPECIFIC
 *         )
 *     )
 * )
 * ```
 *
 * @property config Configuration for the plugin
 */
class PrivacyLayerPlugin(
    private val config: PrivacyLayerConfig = PrivacyLayerConfig(),
) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    private val eventScanner = EventScanner(config)
    private val piiRedactor = PiiRedactor(config)

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        amplitude.logger.debug("PrivacyLayerPlugin: Initialized with config: $config")
    }

    /**
     * Executes the plugin logic on an event.
     * Scans for PII and redacts it according to the configuration.
     *
     * @param event The event to process
     * @return The event with PII redacted, or null to drop the event
     */
    override fun execute(event: BaseEvent): BaseEvent? {
        try {
            // Scan each configured field
            if (ScanField.EVENT_PROPERTIES in config.scanFields) {
                event.eventProperties = processMap(event.eventProperties)
            }

            if (ScanField.USER_PROPERTIES in config.scanFields) {
                event.userProperties = processMap(event.userProperties)
            }

            if (ScanField.GROUP_PROPERTIES in config.scanFields) {
                event.groupProperties = processMap(event.groupProperties)
            }

            if (ScanField.EXTRA in config.scanFields) {
                event.extra = processMapForExtra(event.extra)
            }

            return event
        } catch (e: Exception) {
            amplitude.logger.error("PrivacyLayerPlugin: Error processing event: ${e.message}")
            // Fail open: return event without redaction to avoid blocking events
            return event
        }
    }

    /**
     * Process a map, scanning all string values for PII and redacting them.
     */
    private fun processMap(map: Map<String, Any?>?): MutableMap<String, Any?>? {
        if (map == null) return null

        val result = mutableMapOf<String, Any?>()
        val keysToRemove = mutableSetOf<String>()

        for ((key, value) in map) {
            val processedValue = processValue(value)

            // If redaction strategy is REMOVE and value was redacted, mark key for removal
            if (processedValue == REMOVED_MARKER) {
                keysToRemove.add(key)
            } else {
                result[key] = processedValue
            }
        }

        return if (result.isEmpty() && keysToRemove.isNotEmpty()) {
            null // All properties were removed
        } else {
            result
        }
    }

    /**
     * Process the extra map, which has a non-nullable value type (Map<String, Any>).
     * Different from processMap due to type signature requirements.
     */
    private fun processMapForExtra(map: Map<String, Any>?): Map<String, Any>? {
        if (map == null) return null

        val result = mutableMapOf<String, Any>()
        val keysToRemove = mutableSetOf<String>()

        for ((key, value) in map) {
            val processedValue = processValue(value)

            // If redaction strategy is REMOVE and value was redacted, mark key for removal
            when {
                processedValue == REMOVED_MARKER -> keysToRemove.add(key)
                processedValue != null -> result[key] = processedValue
                // Skip null values as extra map doesn't support nullable values
            }
        }

        return if (result.isEmpty() && keysToRemove.isNotEmpty()) {
            null // All properties were removed
        } else {
            result
        }
    }

    /**
     * Process a value recursively.
     * Handles strings, nested maps, arrays, and other types.
     */
    private fun processValue(value: Any?): Any? {
        return when (value) {
            is String -> processString(value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                processMap(value as? Map<String, Any?>)
            }
            is List<*> -> processList(value)
            is Array<*> -> processList(value.toList())
            else -> value // Numbers, booleans, null, etc. pass through
        }
    }

    /**
     * Process a list, scanning all values recursively.
     */
    private fun processList(list: List<*>): List<*> {
        return list.mapNotNull { item ->
            val processed = processValue(item)
            if (processed == REMOVED_MARKER) null else processed
        }
    }

    /**
     * Process a string, detecting and redacting PII.
     */
    private fun processString(text: String): Any {
        // Skip empty or very large strings
        if (text.isEmpty() || text.length > config.maxTextLength) {
            return text
        }

        // Phase 1: Basic implementation without PII detection
        // For now, just return the text as-is
        // Phase 2 will add actual PII detection with MLKit and regex

        // Detect PII (placeholder for Phase 2)
        val detectedPii = eventScanner.scanText(text)

        // If no PII detected, return original text
        if (detectedPii.isEmpty()) {
            return text
        }

        // Redact detected PII
        return piiRedactor.redact(text, detectedPii)
    }

    override fun teardown() {
        super.teardown()
        amplitude.logger.debug("PrivacyLayerPlugin: Teardown")
    }

    companion object {
        /**
         * Internal marker for removed values.
         * Used to signal that a property should be removed entirely.
         */
        private const val REMOVED_MARKER = "__REMOVED__"
    }
}
