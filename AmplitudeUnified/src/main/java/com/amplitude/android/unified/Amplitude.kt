package com.amplitude.android.unified

import android.content.Context
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.unified.plugins.AmplitudeSessionReplayPlugin
import com.amplitude.id.IdentityConfiguration

open class Amplitude(
    configuration: Configuration,
) : Amplitude(configuration) {
    private val context: Context = configuration.context

    override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
        super.buildInternal(identityConfiguration)
        add(AmplitudeSessionReplayPlugin(context = context))
    }
}
