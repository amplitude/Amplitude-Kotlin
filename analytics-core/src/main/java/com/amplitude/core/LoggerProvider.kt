package com.amplitude.core

import com.amplitude.common.Logger

public interface LoggerProvider {
    public fun getLogger(amplitude: Amplitude): Logger
}
