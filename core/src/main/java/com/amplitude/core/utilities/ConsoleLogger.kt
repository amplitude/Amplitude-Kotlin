package com.amplitude.core.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.Logger
import com.amplitude.core.Logger.LogMode
import com.amplitude.core.LoggerProvider

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
