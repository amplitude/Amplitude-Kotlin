package com.amplitude.android

import android.content.Context
import com.amplitude.Amplitude
import com.amplitude.platform.plugins.AmplitudeDestination

open class Amplitude(
    val context: Context,
    configuration: AndroidConfiguration
): com.amplitude.Amplitude(configuration) {

    override fun build() {
        add(AmplitudeDestination())
    }
}
