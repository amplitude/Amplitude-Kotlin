package com.amplitude.core.utilities

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

internal fun Map<*, *>?.toJSONObject(): JSONObject? {
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

internal fun JSONObject.toMapObj(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    for (key in this.keys()) {
        map[key] = this[key].fromJSON()
    }
    return map
}

internal fun JSONArray.toListObj(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until this.length()) {
        val value = this[i].fromJSON()
        list.add(value)
    }
    return list
}

internal fun Collection<*>?.toJSONArray(): JSONArray? {
    if (this == null) {
        return null
    }
    val jsonArray = JSONArray()
    for (element in this) {
        jsonArray.put(element.toJSON())
    }
    return jsonArray
}

internal fun Array<*>?.toJSONArray(): JSONArray? {
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
        is JSONObject -> this.toMapObj()
        is JSONArray -> this.toListObj()
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
        is Collection<*> -> this.toJSONArray()
        is Array<*> -> this.toJSONArray()
        else -> this
    }
}

/**
 * Recursively deep-copies a map so that nested maps and collections
 * are also independent copies. Severs all references to the caller's data structures,
 * preventing [ConcurrentModificationException] when the SDK pipeline processes the
 * event on a background thread while the caller continues to mutate the original.
 */
internal fun Map<String, Any?>.deepCopy(): MutableMap<String, Any?> {
    val copy = LinkedHashMap<String, Any?>(size)
    for ((key, value) in this) {
        copy[key] = value.deepCopyValue()
    }
    return copy
}

private fun Any?.deepCopyValue(): Any? {
    return when (this) {
        is Map<*, *> -> {
            val copy = LinkedHashMap<Any?, Any?>(size)
            for ((k, v) in this) {
                copy[k] = v.deepCopyValue()
            }
            copy
        }
        is Collection<*> -> mapTo(ArrayList(size)) { it.deepCopyValue() }
        else -> this // primitives, strings, arrays — immutable or no fail-fast iterator
    }
}
