package com.amplitude.core.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encoded payload for transmission to the diagnostics server.
 */
internal data class DiagnosticsPayload(
    val tags: Map<String, String>?,
    val counters: Map<String, Long>?,
    val histograms: Map<String, HistogramResult>?,
    val events: List<DiagnosticsEvent>?,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject().apply {
            tags?.let { t ->
                put(
                    "tags",
                    JSONObject().apply {
                        t.forEach { (key, value) -> put(key, value) }
                    },
                )
            }

            counters?.let { c ->
                put(
                    "counters",
                    JSONObject().apply {
                        c.forEach { (key, value) -> put(key, value) }
                    },
                )
            }

            histograms?.let { h ->
                put(
                    "histogram",
                    JSONObject().apply {
                        h.forEach { (key, value) -> put(key, value.toJSONObject()) }
                    },
                )
            }

            events?.let { e ->
                put(
                    "events",
                    JSONArray().apply {
                        e.forEach { event -> put(event.toJSONObject()) }
                    },
                )
            }
        }
    }

    fun toJsonString(): String = toJSONObject().toString()
}
