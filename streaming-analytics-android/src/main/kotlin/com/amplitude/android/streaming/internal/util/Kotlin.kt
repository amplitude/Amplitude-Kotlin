package com.amplitude.android.streaming.internal.util

internal fun Map<String, Any?>.deepCopy(): MutableMap<String, Any?> {
    val copy = LinkedHashMap<String, Any?>(size)
    for ((key, value) in this) {
        copy[key] = value.deepCopyValue()
    }
    return copy
}

private fun Any?.deepCopyValue(): Any? =
    when (this) {
        is Map<*, *> -> {
            val copy = LinkedHashMap<Any?, Any?>(size)
            for ((k, v) in this) {
                copy[k] = v.deepCopyValue()
            }
            copy
        }
        is Collection<*> -> mapTo(ArrayList(size)) { it.deepCopyValue() }
        else -> this
    }

internal fun Long.millisToSeconds(): Double = this / 1_000.0
