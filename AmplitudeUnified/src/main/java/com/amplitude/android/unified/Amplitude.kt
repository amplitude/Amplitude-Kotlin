package com.amplitude.android.unified

import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.unified.plugins.AmplitudeSessionReplayPlugin
import com.amplitude.id.IdentityConfiguration

open class Amplitude(configuration: Configuration) : Amplitude(configuration) {
    override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
        super.buildInternal(identityConfiguration)
        add(AmplitudeSessionReplayPlugin())
    }
}
