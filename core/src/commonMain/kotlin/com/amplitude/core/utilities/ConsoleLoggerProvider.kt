package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.LoggerProvider

class ConsoleLoggerProvider() : LoggerProvider {
    private val logger: Logger by lazy {
        ConsoleLogger()
    }

    override fun getLogger(amplitude: Amplitude): Logger {
        return logger
    }
}
