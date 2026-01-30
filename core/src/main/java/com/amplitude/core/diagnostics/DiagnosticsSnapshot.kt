package com.amplitude.core.diagnostics

/**
 * In-memory snapshot of all collected diagnostic data.
 * Used for transferring data between storage and client for upload.
 */
internal data class DiagnosticsSnapshot(
    val tags: Map<String, String>?,
    val counters: Map<String, Long>?,
    val histograms: Map<String, HistogramSnapshot>?,
    val events: List<DiagnosticsEvent>?,
) {
    fun isEmpty(): Boolean {
        return counters.isNullOrEmpty() && histograms.isNullOrEmpty() && events.isNullOrEmpty()
    }

    fun toPayload(): DiagnosticsPayload {
        return DiagnosticsPayload(
            tags = tags,
            counters = counters,
            histograms = histograms?.mapValues { (_, histogram) -> histogram.toResult() },
            events = events,
        )
    }

    companion object {
        fun empty(): DiagnosticsSnapshot {
            return DiagnosticsSnapshot(
                tags = emptyMap(),
                counters = emptyMap(),
                histograms = emptyMap(),
                events = emptyList(),
            )
        }
    }
}
