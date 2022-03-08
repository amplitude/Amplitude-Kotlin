package com.amplitude.android.utilities

import android.util.Log
import com.amplitude.android.shared.AndroidLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.Logger
import com.amplitude.core.LoggerProvider

class AndroidLogger() : Logger {
    override var logMode: Logger.LogMode = Logger.LogMode.INFO
        set(value) {
            when (value) {
                Logger.LogMode.OFF -> {
                    logger.setEnableLogging(false)
                }
                Logger.LogMode.WARN -> {
                    logger.setLogLevel(Log.WARN)
                }
                Logger.LogMode.DEBUG -> {
                    logger.setLogLevel(Log.DEBUG)
                }
                Logger.LogMode.ERROR -> {
                    logger.setLogLevel(Log.ERROR)
                }
                else -> {
                    logger.setLogLevel(Log.INFO)
                }
            }
            field = value
        }
    private val logger = AndroidLogger.logger
    private val tag = "Amplitude"

    override fun debug(message: String) {
        logger.d(tag, message)
    }

    override fun error(message: String) {
        logger.e(tag, message)
    }

    override fun info(message: String) {
        logger.i(tag, message)
    }

    override fun warn(message: String) {
        logger.w(tag, message)
    }
}

class AndroidLoggerProvider() : LoggerProvider {
    override fun getLogger(amplitude: Amplitude): Logger {
        return AndroidLogger()
    }
}
