package com.amplitude.core

import com.amplitude.common.Logger

interface LoggerProvider {
    fun getLogger(amplitude: Amplitude): Logger
}
