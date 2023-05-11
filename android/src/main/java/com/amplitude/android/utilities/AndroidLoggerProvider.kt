package com.amplitude.android.utilities

import com.amplitude.common.Logger
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.LoggerProvider

class AndroidLoggerProvider() : LoggerProvider {
    private val logger: Logger by lazy {
        LogcatLogger()
    }

    override fun getLogger(amplitude: Amplitude): Logger {
        return logger
    }
}
