package com.amplitude.android.utilities

import com.amplitude.common.Logger

object ExceptionUtils {

    fun <T> safeInvoke(action: () -> T?): T? {
        return safeInvoke(null, action)
    }
    fun <T> safeInvoke(logger: Logger?, action: () -> T?): T? {
        try {
            return action()
        } catch (e: Throwable) {
            logger?.warn("SafeInvoke suppressed exception: ${e.message}")
            logger?.warn(e.stackTraceToString())
            return null
        }
    }
}