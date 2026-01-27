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

    @Synchronized
    fun record(value: Double) {
        count++
        min = kotlin.math.min(min, value)
        max = kotlin.math.max(max, value)
        sum += value
    }

    @Synchronized
    fun toResult(avg: Double): HistogramResult {
        return HistogramResult(
            count = count,
            min = min,
            max = max,
            avg = avg,
        )
    }

    @Synchronized
    fun merge(other: HistogramStats) {
        if (other.count == 0L) return
        count += other.count
        min = kotlin.math.min(min, other.min)
        max = kotlin.math.max(max, other.max)
        sum += other.sum
    }

    @Synchronized
    fun snapshot(): HistogramSnapshot {
        return HistogramSnapshot(count = count, min = min, max = max, sum = sum)
    }

    companion object {
        fun fromResult(result: HistogramResult): HistogramStats {
            return HistogramStats().apply {
                count = result.count
                min = result.min
                max = result.max
                sum = result.avg * result.count
            }
        }

        fun fromJSONObject(json: JSONObject): HistogramStats {
            return HistogramStats().apply {
                count = json.getLong("count")
                min = json.getDouble("min")
                max = json.getDouble("max")
                sum = json.getDouble("sum")
            }
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
}

internal data class HistogramSnapshot(
    val count: Long,
    val min: Double,
    val max: Double,
    val sum: Double,
)

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
