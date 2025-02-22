package com.amplitude.common.jvm

import com.amplitude.common.Logger

/**
 * Console logger
 */
class ConsoleLogger() : Logger {
    override var logMode: Logger.LogMode = Logger.LogMode.INFO

    override fun debug(message: String) {
        log(Logger.LogMode.DEBUG, message)
    }

    override fun error(message: String) {
        log(Logger.LogMode.ERROR, message)
    }

    override fun info(message: String) {
        log(Logger.LogMode.INFO, message)
    }

    override fun warn(message: String) {
        log(Logger.LogMode.WARN, message)
    }

    private fun log(logLevel: Logger.LogMode, message: String) {
        if (logMode <= logLevel) {
            println(message)
        }
    }

    companion object {
        val logger = ConsoleLogger()
    }
}
