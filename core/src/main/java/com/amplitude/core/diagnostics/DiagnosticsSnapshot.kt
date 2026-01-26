package com.amplitude.core.diagnostics

/**
 * In-memory snapshot of all collected diagnostic data.
 * Used for transferring data between storage and client for upload.
 */
internal data class DiagnosticsSnapshot(
    val tags: Map<String, String>,
    val counters: Map<String, Long>,
    val histograms: Map<String, HistogramStats>,
    val events: List<DiagnosticsEvent>,
) {
    fun isEmpty(): Boolean {
        return tags.isEmpty() && counters.isEmpty() && histograms.isEmpty() && events.isEmpty()
    }

    fun merge(other: DiagnosticsSnapshot): DiagnosticsSnapshot {
        val mergedTags = tags.toMutableMap().apply { putAll(other.tags) }

        val mergedCounters = counters.toMutableMap()
        for ((key, value) in other.counters) {
            val current = mergedCounters[key] ?: 0L
            mergedCounters[key] = current + value
        }

        val mergedHistograms = mutableMapOf<String, HistogramStats>()
        for ((key, stats) in histograms) {
            if (stats.snapshot().count <= 0L) continue
            val copy = HistogramStats()
            copy.merge(stats)
            mergedHistograms[key] = copy
        }
        for ((key, stats) in other.histograms) {
            if (stats.snapshot().count <= 0L) continue
            val existing = mergedHistograms[key]
            if (existing != null) {
                existing.merge(stats)
            } else {
                val copy = HistogramStats()
                copy.merge(stats)
                mergedHistograms[key] = copy
            }
        }

        val mergedEvents = events + other.events

        return DiagnosticsSnapshot(
            tags = mergedTags,
            counters = mergedCounters,
            histograms = mergedHistograms,
            events = mergedEvents,
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
