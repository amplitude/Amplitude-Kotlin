package com.amplitude.common.android

import android.util.Log
import com.amplitude.common.Logger

class LogcatLogger() : Logger {
    override var logMode: Logger.LogMode = Logger.LogMode.INFO
    private val tag = "Amplitude"

    override fun debug(message: String) {
        if (logMode <= Logger.LogMode.DEBUG) {
            Log.d(tag, message)
        }
    }

    override fun error(message: String) {
        if (logMode <= Logger.LogMode.ERROR) {
            Log.e(tag, message)
        }
    }

    override fun info(message: String) {
        if (logMode <= Logger.LogMode.INFO) {
            Log.i(tag, message)
        }
    }

    override fun warn(message: String) {
        if (logMode <= Logger.LogMode.WARN) {
            Log.w(tag, message)
        }
    }

    companion object {
        val logger = LogcatLogger()
    }
}
