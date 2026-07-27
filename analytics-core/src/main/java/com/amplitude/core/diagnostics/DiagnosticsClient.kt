package com.amplitude.core.diagnostics

import com.amplitude.core.RestrictedAmplitudeFeature

/**
 * Interface for diagnostic tracking operations.
 * Provides methods to record tags, counters, histograms, and events
 * for SDK diagnostics and telemetry.
 */
@RestrictedAmplitudeFeature
public interface DiagnosticsClient {
    /**
     * Set a tag with the given name and value.
     * Tags are metadata labels associated with diagnostics data.
     *
     * @param name The tag name
     * @param value The tag value
     */
    public fun setTag(
        name: String,
        value: String,
    )

    /**
     * Set multiple tags at once.
     *
     * @param tags Map of tag names to values
     */
    public fun setTags(tags: Map<String, String>)

    /**
     * Increment a counter by the specified size.
     * Counters are numeric aggregates that accumulate over time.
     *
     * @param name The counter name
     * @param size The amount to increment (default 1)
     */
    public fun increment(
        name: String,
        size: Long = 1,
    )

    /**
     * Record a value for a histogram metric.
     * Histograms track distribution data (min, max, sum, count, average).
     *
     * @param name The histogram name
     * @param value The value to record
     */
    public fun recordHistogram(
        name: String,
        value: Double,
    )

    /**
     * Record a diagnostic event.
     *
     * @param name The event name
     * @param properties Optional properties map for the event
     */
    public fun recordEvent(
        name: String,
        properties: Map<String, Any>? = null,
    )

    /**
     * Flush all collected diagnostics data to the server.
     */
    public fun flush()

    /**
     * Close the diagnostics client and release resources.
     */
    public fun close()
}
