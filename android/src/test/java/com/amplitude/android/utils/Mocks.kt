package com.amplitude.android.utils

import com.amplitude.android.utilities.SystemTime
import io.mockk.every
import io.mockk.mockkObject

fun mockSystemTime(timestamp: Long): Long {
    mockkObject(SystemTime)

    every { SystemTime.getCurrentTimeMillis() } returns timestamp

    return timestamp
}
