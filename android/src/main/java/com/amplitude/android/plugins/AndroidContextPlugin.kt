package com.amplitude.android.plugins

import com.amplitude.android.BuildConfig
import com.amplitude.android.Configuration
import com.amplitude.android.TrackingOptions
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.Amplitude
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.plugins.ContextPlugin
import java.util.UUID

@OptIn(RestrictedAmplitudeFeature::class)
open class AndroidContextPlugin : ContextPlugin() {
    private lateinit var contextProvider: AndroidContextProvider

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        val configuration = amplitude.configuration as Configuration
        contextProvider =
            AndroidContextProvider(
                configuration.context,
                configuration.locationListening,
                configuration.trackingOptions.shouldTrackAdid(),
                configuration.trackingOptions.shouldTrackAppSetId(),
            )
        amplitude.diagnosticsClient.setTag(
            name = "sdk.${SDK_LIBRARY}.version",
            value = SDK_VERSION,
        )
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        applyContextData(event)
        return event
    }

    /**
     * Generate a device ID using Android-specific logic.
     * Priority: configuration -> store (if not forceNew) -> advertising ID -> app set ID -> random UUID
     *
     * @param forceNew If true, skip checking the store and generate a fresh deviceId
     * @return the generated device ID (never null for Android)
     */
    fun generateDeviceId(forceNew: Boolean = false): String {
        val configuration = amplitude.configuration as Configuration

        // Check configuration (always respected, even with forceNew)
        configuration.deviceId?.let { return it }

        // Check store (skip if forcing new)
        if (!forceNew) {
            amplitude.store.deviceId?.let { deviceId ->
                if (validDeviceId(deviceId) && !deviceId.endsWith("S")) {
                    return deviceId
                }
            }
        }

        // Check advertising ID (if enabled and not per-install)
        if (!configuration.newDeviceIdPerInstall &&
            configuration.useAdvertisingIdForDeviceId &&
            !contextProvider.isLimitAdTrackingEnabled()
        ) {
            contextProvider.advertisingId?.let { advertisingId ->
                if (validDeviceId(advertisingId)) {
                    return advertisingId
                }
            }
        }

        // Check app set ID (if enabled)
        if (configuration.useAppSetIdForDeviceId) {
            contextProvider.appSetId?.let { appSetId ->
                if (validDeviceId(appSetId)) {
                    return "${appSetId}S"
                }
            }
        }

        // Generate random ID
        return UUID.randomUUID().toString() + "R"
    }

    @Deprecated(
        message = "Use generateDeviceId() instead. Amplitude now handles setting the deviceId.",
        replaceWith = ReplaceWith("generateDeviceId()"),
    )
    fun initializeDeviceId(configuration: Configuration) {
        amplitude.setDeviceId(generateDeviceId(forceNew = false))
    }

    @Deprecated(
        message = "Use Amplitude.setDeviceId() instead. The plugin should not set identity directly.",
        replaceWith = ReplaceWith("amplitude.setDeviceId(deviceId)"),
    )
    protected open fun setDeviceId(deviceId: String) {
        amplitude.setDeviceId(deviceId)
    }

    private fun applyContextData(event: BaseEvent) {
        val configuration = amplitude.configuration as Configuration
        event.timestamp ?: let {
            val eventTime = System.currentTimeMillis()
            event.timestamp = eventTime
        }
        event.insertId ?: let {
            event.insertId = UUID.randomUUID().toString()
        }
        event.library ?: let {
            event.library = "$SDK_LIBRARY/$SDK_VERSION"
        }
        event.userId ?: let {
            event.userId = amplitude.store.userId
        }
        event.deviceId ?: let {
            event.deviceId = amplitude.store.deviceId
        }
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
        if (trackingOptions.shouldTrackIpAddress()) {
            event.ip ?: let {
                // get the ip in server side if there is no event level ip
                event.ip = "\$remote"
            }
        }
        if (trackingOptions.shouldTrackCountry() && event.ip !== "\$remote") {
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
        event.partnerId ?: let {
            amplitude.configuration.partnerId ?. let {
                event.partnerId = it
            }
        }
        event.plan ?: let {
            amplitude.configuration.plan ?. let {
                event.plan = it.clone()
            }
        }
        event.ingestionMetadata ?: let {
            amplitude.configuration.ingestionMetadata ?. let {
                event.ingestionMetadata = it.clone()
            }
        }
    }

    companion object {
        const val PLATFORM = "Android"
        const val SDK_LIBRARY = "amplitude-analytics-android"
        const val SDK_VERSION = BuildConfig.AMPLITUDE_VERSION
        private val INVALID_DEVICE_IDS =
            setOf("", "9774d56d682e549c", "unknown", "000000000000000", "Android", "DEFACE", "00000000-0000-0000-0000-000000000000")

        fun validDeviceId(deviceId: String): Boolean {
            return !(deviceId.isEmpty() || INVALID_DEVICE_IDS.contains(deviceId))
        }
    }
}
