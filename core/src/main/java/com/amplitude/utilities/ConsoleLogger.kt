package com.amplitude.utilities

import com.amplitude.Amplitude
import com.amplitude.Logger
import com.amplitude.LoggerProvider
import com.amplitude.Logger.LogMode

class ConsoleLogger() : Logger {
    override var logMode: LogMode = LogMode.INFO

    override fun debug(message: String) {
        log(LogMode.DEBUG, message)
    }

    override fun error(message: String) {
        log(LogMode.ERROR, message)
    }

    override fun info(message: String) {
        log(LogMode.INFO, message)
    }

    override fun warn(message: String) {
        log(LogMode.WARN, message)
    }

    private fun log(logLevel: Logger.LogMode, message: String) {
        if (logMode <= logLevel) {
            println(message)
        }
    }
}

class ConsoleLoggerProvider() : LoggerProvider {
    override fun getLogger(amplitude: Amplitude): Logger {
        return ConsoleLogger()
    }
}