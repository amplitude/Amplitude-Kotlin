package com.amplitude.android

import android.content.Context
import com.amplitude.Amplitude
import com.amplitude.platform.plugins.AmplitudeDestination

open class AndroidAmplitude(
    val context: Context,
    configuration: AndroidConfiguration
): Amplitude(configuration) {

    override fun build() {
        add(AmplitudeDestination())
    }
}
