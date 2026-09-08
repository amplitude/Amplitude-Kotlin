package com.amplitude.android.streaming.internal.util

import android.os.SystemClock
import com.amplitude.android.streaming.internal.StreamingDiGraph

internal val StreamingDiGraph.time: Time by DiGraph.weak { Time() }

internal class Time {
    fun nowMillis(): Long = System.currentTimeMillis()

    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
