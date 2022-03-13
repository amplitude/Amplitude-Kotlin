package com.amplitude.core.utilities

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

fun Map<*, *>?.toJSONObject(): JSONObject? {
    if (this == null) {
        return null
    }
    val jsonObject = JSONObject()
    for (entry in entries) {
        val key = entry.key as? String ?: continue
        val value = entry.value.toJSON()
        jsonObject.put(key, value)
    }
    return jsonObject
}

internal fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    for (key in this.keys()) {
        map[key] = this[key].fromJSON()
    }
    return map
}

internal fun JSONArray.toList(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until this.length()) {
        val value = this[i].fromJSON()
        list.add(value)
    }
    return list
}

internal fun List<*>?.toJSONArray(): JSONArray? {
    if (this == null) {
        return null
    }
    val jsonArray = JSONArray()
    for (element in this) {
        jsonArray.put(element.toJSON())
    }
    return jsonArray
}

private fun Any?.fromJSON(): Any? {
    return when (this) {
        is JSONObject -> this.toMap()
        is JSONArray -> this.toList()
        // org.json uses BigDecimal for doubles and floats; normalize to double
        // to make testing for equality easier.
        is BigDecimal -> this.toDouble()
        JSONObject.NULL -> null
        else -> this
    }
}

private fun Any?.toJSON(): Any? {
    return when (this) {
        is Map<*, *> -> this.toJSONObject()
        is List<*> -> this.toJSONArray()
        else -> this
    }
}
