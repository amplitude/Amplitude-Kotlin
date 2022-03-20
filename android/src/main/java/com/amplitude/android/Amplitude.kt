package com.amplitude.android

import android.content.Context
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.utilities.AnalyticsIdentityListener
import com.amplitude.core.utilities.FileStorage
import com.amplitude.id.FileIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityContainer
import kotlinx.coroutines.launch

open class Amplitude(
    configuration: Configuration
) : Amplitude(configuration) {

    override fun build() {
        val storageDirectory = (configuration as Configuration).context.getDir("${FileStorage.STORAGE_PREFIX}-${configuration.instanceName}", Context.MODE_PRIVATE)
        idContainer = IdentityContainer.getInstance(
            IdentityConfiguration(
                instanceName = configuration.instanceName,
                apiKey = configuration.apiKey,
                identityStorageProvider = FileIdentityStorageProvider(),
                storageDirectory = storageDirectory
            )
        )
        idContainer.identityManager.addIdentityListener(AnalyticsIdentityListener(store))
        amplitudeScope.launch(amplitudeDispatcher) {
            add(AndroidContextPlugin())
        }
        add(AmplitudeDestination())
    }
}
