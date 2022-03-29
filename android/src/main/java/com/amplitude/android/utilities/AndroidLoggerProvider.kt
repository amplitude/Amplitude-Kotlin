package com.amplitude.android.utilities

import com.amplitude.common.Logger
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.LoggerProvider

class AndroidLoggerProvider() : LoggerProvider {
    override fun getLogger(amplitude: Amplitude): Logger {
        return LogcatLogger()
    }
}
