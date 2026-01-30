package com.amplitude.core.diagnostics

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a diagnostic event with timestamp and optional properties.
 */
internal data class DiagnosticsEvent(
    val eventName: String,
    val time: Double,
    val eventProperties: Map<String, Any?>? = null,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("event_name", eventName)
        json.put("time", time)
        eventProperties?.let {
            val propsJson = JSONObject()
            for ((key, value) in it) {
                propsJson.put(key, toJsonValue(value))
            }
            json.put("event_properties", propsJson)
        }
        return json
    }

    fun toJsonString(): String = toJSONObject().toString()

    companion object {
        fun fromJSONObject(json: JSONObject): DiagnosticsEvent? {
            val name = json.optString("event_name", "")
            if (name.isBlank()) return null
            val time = json.optDouble("time", Double.NaN)
            if (time.isNaN()) return null
            val propsJson = json.optJSONObject("event_properties")
            val properties =
                if (propsJson != null) {
                    val map = mutableMapOf<String, Any?>()
                    for (key in propsJson.keys()) {
                        map[key] = fromJsonValue(propsJson.opt(key))
                    }
                    map
                } else {
                    null
                }
            return DiagnosticsEvent(name, time, properties)
        }

        fun fromJsonString(jsonString: String): DiagnosticsEvent? {
            return fromJSONObject(JSONObject(jsonString))
        }

        private fun toJsonValue(value: Any?): Any? {
            return when (value) {
                is Map<*, *> -> {
                    val obj = JSONObject()
                    for ((key, nested) in value) {
                        if (key is String) {
                            obj.put(key, toJsonValue(nested))
                        }
                    }
                    obj
                }
                is List<*> -> {
                    val array = JSONArray()
                    for (item in value) {
                        array.put(toJsonValue(item))
                    }
                    array
                }
                else -> value
            }
        }

        private fun fromJsonValue(value: Any?): Any? {
            return when (value) {
                JSONObject.NULL -> null
                is JSONObject -> {
                    val map = mutableMapOf<String, Any?>()
                    for (key in value.keys()) {
                        map[key] = fromJsonValue(value.get(key))
                    }
                    map
                }
                is JSONArray -> {
                    val list = mutableListOf<Any?>()
                    for (i in 0 until value.length()) {
                        list.add(fromJsonValue(value.get(i)))
                    }
                    list
                }
                else -> value
            }
        }
    }
}
