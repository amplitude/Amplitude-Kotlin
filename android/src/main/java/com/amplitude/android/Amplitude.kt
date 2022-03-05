package com.amplitude.android

import com.amplitude.core.Amplitude
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.utilities.AnalyticsIdentityListener
import com.amplitude.id.FileIdentityStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdConfiguration
import com.amplitude.id.IdContainer

open class Amplitude(
    configuration: Configuration
): Amplitude(configuration) {

    override fun build() {
        idContainer = IdContainer.getInstance(IdConfiguration(instanceName = configuration.instanceName, apiKey = configuration.apiKey, identityStorageProvider = FileIdentityStorageProvider()))
        idContainer.identityManager.addIdentityListener(AnalyticsIdentityListener(store))
        add(AmplitudeDestination())
    }
}
