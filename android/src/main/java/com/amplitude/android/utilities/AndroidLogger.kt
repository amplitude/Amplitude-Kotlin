package com.amplitude.android.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.Logger
import com.amplitude.core.LoggerProvider

class AndroidLogger() : Logger {
    override var logMode: Logger.LogMode = Logger.LogMode.INFO

    override fun debug(message: String) {
        TODO("Not yet implemented")
    }

    override fun error(message: String) {
        TODO("Not yet implemented")
    }

    override fun info(message: String) {
        TODO("Not yet implemented")
    }

    override fun warn(message: String) {
        TODO("Not yet implemented")
    }
}

class AndroidLoggerProvider() : LoggerProvider {
    override fun getLogger(amplitude: Amplitude): Logger {
        return AndroidLogger()
    }
}
