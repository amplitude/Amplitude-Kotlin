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
        val json = JSONObject()


        if (tags != null) {
            val tagsJson = JSONObject()
            tags.forEach { (key, value) ->
                tagsJson.put(key, value)
            }
            json.put("tags", tagsJson)
        }


        if (counters != null) {
            val countersJson = JSONObject()
            counters.forEach { (key, value) ->
                countersJson.put(key, value)
            }
            json.put("counters", countersJson)
        }

        if (histograms != null) {
            val histogramJson = JSONObject()
            histograms.forEach { (key, value) ->
                histogramJson.put(key, value.toJSONObject())
            }
            json.put("histogram", histogramJson)
        }

        if (events != null) {
            val eventsJson = JSONArray()
            events.forEach { event ->
                eventsJson.put(event.toJSONObject())
            }
            json.put("events", eventsJson)
        }

        return json
    }

    fun toJsonString(): String = toJSONObject().toString()
}
