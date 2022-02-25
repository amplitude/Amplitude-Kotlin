package com.amplitude.android

import com.amplitude.core.Amplitude
import com.amplitude.core.platform.plugins.AmplitudeDestination

open class Amplitude(
    configuration: Configuration
): Amplitude(configuration) {

    override fun build() {
        add(AmplitudeDestination())
    }
}
