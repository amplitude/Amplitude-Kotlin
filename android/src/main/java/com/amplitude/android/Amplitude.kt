package com.amplitude.android

import com.amplitude.platform.plugins.AmplitudeDestination

open class Amplitude(
    configuration: Configuration
): com.amplitude.Amplitude(configuration) {

    override fun build() {
        add(AmplitudeDestination())
    }
}
