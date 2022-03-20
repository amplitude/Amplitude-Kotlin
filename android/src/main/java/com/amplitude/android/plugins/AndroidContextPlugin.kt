package com.amplitude.android.plugins

import com.amplitude.android.Configuration
import com.amplitude.android.TrackingOptions
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin

class AndroidContextPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    private lateinit var contextProvider: AndroidContextProvider

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        val configuration = amplitude.configuration as Configuration
        contextProvider = AndroidContextProvider(configuration.context, configuration.locationListening)
        initializeDeviceId(configuration)
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        applyContextData(event)
        return event
    }

    private fun initializeDeviceId(configuration: Configuration) {
        val deviceId = amplitude.store.deviceId
        if (deviceId != null && validDeviceId(deviceId) && !deviceId.endsWith("S")) {
            return
        }
        if (!configuration.newDeviceIdPerInstall && configuration.useAdvertisingIdForDeviceId && !contextProvider.isLimitAdTrackingEnabled()) {
            val advertisingId = contextProvider.advertisingId
            if (validDeviceId(advertisingId)) {
                amplitude.setDeviceId(advertisingId)
                return
            }
        }
        if (configuration.useAppSetIdForDeviceId) {
            val appSetId = contextProvider.appSetId
            if (validDeviceId(appSetId)) {
                amplitude.setDeviceId("{$appSetId}S")
                return
            }
        }
        val randomId = AndroidContextProvider.generateUUID() + "R"
        amplitude.setDeviceId(randomId)
    }

    private fun applyContextData(event: BaseEvent) {
        val configuration = amplitude.configuration as Configuration
        event.library = "$SDK_LIBRARY/$SDK_VERSION"
        event.userId = amplitude.store.userId
        event.deviceId = amplitude.store.deviceId
        val trackingOptions = configuration.trackingOptions
        if (configuration.enableCoppaControl) {
            trackingOptions.mergeIn(TrackingOptions.forCoppaControl())
        }
        if (trackingOptions.shouldTrackVersionName()) {
            event.versionName = contextProvider.versionName
        }
        if (trackingOptions.shouldTrackOsName()) {
            event.osName = contextProvider.osName
        }
        if (trackingOptions.shouldTrackOsVersion()) {
            event.osVersion = contextProvider.osVersion
        }
        if (trackingOptions.shouldTrackDeviceBrand()) {
            event.deviceBrand = contextProvider.brand
        }
        if (trackingOptions.shouldTrackDeviceManufacturer()) {
            event.deviceManufacturer = contextProvider.manufacturer
        }
        if (trackingOptions.shouldTrackDeviceModel()) {
            event.deviceModel = contextProvider.model
        }
        if (trackingOptions.shouldTrackCarrier()) {
            event.carrier = contextProvider.carrier
        }
        if (trackingOptions.shouldTrackCountry()) {
            event.country = contextProvider.country
        }
        if (trackingOptions.shouldTrackLanguage()) {
            event.language = contextProvider.language
        }
        if (trackingOptions.shouldTrackPlatform()) {
            event.platform = PLATFORM
        }
        if (trackingOptions.shouldTrackLatLng()) {
            contextProvider.mostRecentLocation?.let {
                event.locationLat = it.latitude
                event.locationLng = it.longitude
            }
        }
        if (trackingOptions.shouldTrackAdid()) {
            contextProvider.advertisingId?.let {
                event.adid = it
            }
        }
        if (trackingOptions.shouldTrackAppSetId()) {
            contextProvider.appSetId?.let {
                event.appSetId = it
            }
        }
    }

    companion object {
        const val PLATFORM = "Android"
        const val SDK_LIBRARY = "amplitude-android-kotlin"
        const val SDK_VERSION = "0.0.1"
        private val INVALID_DEVICE_IDS = setOf("", "9774d56d682e549c", "unknown", "000000000000000", "Android", "DEFACE", "00000000-0000-0000-0000-000000000000")
        fun validDeviceId(deviceId: String): Boolean {
            return !(deviceId.isEmpty() || INVALID_DEVICE_IDS.contains(deviceId))
        }
    }
}
