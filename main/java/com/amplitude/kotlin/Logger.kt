package com.amplitude.kotlin

interface Logger {
    enum class LogMode(i: Int) {
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4),
        OFF(5)
    }

    var logMode:Logger.LogMode

    fun debug(tag: String, message: String)

    fun error(tag: String, message: String)

    fun info(tag: String, message: String)

    fun warn(tag: String, message: String)
}

interface LoggerProvider {
    fun getLogger(amplitude: Amplitude): Logger
}