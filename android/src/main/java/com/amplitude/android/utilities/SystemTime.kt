package com.amplitude.android.utilities

/**
 * Class to allow for easy centralization (and mocking) of the current time
 */
internal class SystemTime {
    companion object {
        fun getCurrentTimeMillis(): Long {
            return System.currentTimeMillis()
        }
    }
}
