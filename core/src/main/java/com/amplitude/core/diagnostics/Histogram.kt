package com.amplitude.core.diagnostics

import org.json.JSONObject

/**
 * Tracks histogram statistics including count, min, max, and sum.
 * Thread-safe through synchronized access.
 */
internal class HistogramStats {
    private var count: Long = 0
    private var min: Double = Double.POSITIVE_INFINITY
    private var max: Double = Double.NEGATIVE_INFINITY
    private var sum: Double = 0.0

    fun record(value: Double) {
        count++
        min = kotlin.math.min(min, value)
        max = kotlin.math.max(max, value)
        sum += value
    }

    fun toSnapshot(): HistogramSnapshot {
        return HistogramSnapshot(count = count, min = min, max = max, sum = sum)
    }
}

internal data class HistogramSnapshot(
    val count: Long,
    val min: Double,
    val max: Double,
    val sum: Double,
) {
    companion object {
        fun fromJSONObject(json: JSONObject): HistogramSnapshot {
            return HistogramSnapshot(
                count = json.getLong("count"),
                min = json.getDouble("min"),
                max = json.getDouble("max"),
                sum = json.getDouble("sum"),
            )
        }
    }

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("count", count)
        json.put("min", min)
        json.put("max", max)
        json.put("sum", sum)
        return json
    }

    fun toResult(): HistogramResult {
        return HistogramResult(
            count = count,
            min = min,
            max = max,
            avg = if (count > 0L) sum / count else 0.0,
        )
    }
}

/**
 * Immutable result of histogram aggregation.
 */
internal data class HistogramResult(
    val count: Long,
    val min: Double,
    val max: Double,
    val avg: Double,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("count", count)
        json.put("min", min)
        json.put("max", max)
        json.put("avg", avg)
        return json
    }

    companion object {
        fun fromJSONObject(json: JSONObject): HistogramResult {
            return HistogramResult(
                count = json.getLong("count"),
                min = json.getDouble("min"),
                max = json.getDouble("max"),
                avg = json.getDouble("avg"),
            )
        }
    }
}
