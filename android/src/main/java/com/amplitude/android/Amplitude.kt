package com.amplitude.android

import com.amplitude.core.Amplitude
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.utilities.AnalyticsIdentityListener
import com.amplitude.id.FileIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer

open class Amplitude(
    configuration: Configuration
): Amplitude(configuration) {

    override fun build() {
        idContainer = IdentityContainer.getInstance(IdentityConfiguration(instanceName = configuration.instanceName, apiKey = configuration.apiKey, identityStorageProvider = FileIdentityStorageProvider()))
        idContainer.identityManager.addIdentityListener(AnalyticsIdentityListener(store))
        add(AmplitudeDestination())
    }
}
