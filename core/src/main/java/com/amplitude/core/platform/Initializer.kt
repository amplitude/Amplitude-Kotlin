package com.amplitude.core.platform

import com.amplitude.core.Amplitude

interface Initializer {
    fun execute(amplitude: Amplitude)
}
