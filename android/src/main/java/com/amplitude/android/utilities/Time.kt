package com.amplitude.android.utilities

import android.os.SystemClock

internal class Time {
    fun nowMillis(): Long = System.currentTimeMillis()

    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
