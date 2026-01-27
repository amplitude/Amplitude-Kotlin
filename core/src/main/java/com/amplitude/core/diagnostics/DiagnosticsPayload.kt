package com.amplitude.core.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encoded payload for transmission to the diagnostics server.
 */
internal data class DiagnosticsPayload(
    val tags: Map<String, String>,
    val counters: Map<String, Long>,
    val histogram: Map<String, HistogramResult>,
    val events: List<DiagnosticsEvent>,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        val tagsJson = JSONObject()
        for ((key, value) in tags) {
            tagsJson.put(key, value)
        }
        json.put("tags", tagsJson)

        val countersJson = JSONObject()
        for ((key, value) in counters) {
            countersJson.put(key, value)
        }
        json.put("counters", countersJson)

        val histogramJson = JSONObject()
        for ((key, value) in histogram) {
            histogramJson.put(key, value.toJSONObject())
        }
        json.put("histogram", histogramJson)

        val eventsJson = JSONArray()
        for (event in events) {
            eventsJson.put(event.toJSONObject())
        }
        json.put("events", eventsJson)

        return json
    }

    fun toJsonString(): String = toJSONObject().toString()

    companion object {
        fun fromSnapshot(
            snapshot: DiagnosticsSnapshot,
            histogram: Map<String, HistogramResult>,
        ): DiagnosticsPayload {
            return DiagnosticsPayload(
                tags = snapshot.tags,
                counters = snapshot.counters,
                histogram = histogram,
                events = snapshot.events,
            )
        }
    }
}
