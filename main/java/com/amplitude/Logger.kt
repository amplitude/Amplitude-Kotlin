package com.amplitude

interface Logger {
    enum class LogMode(i: Int) {
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),
        OFF(5)
    }

    var logMode: LogMode

    fun debug(message: String)

    fun error(message: String)

    fun info(message: String)

    fun warn(message: String)
}

interface LoggerProvider {
    fun getLogger(amplitude: Amplitude): Logger
}