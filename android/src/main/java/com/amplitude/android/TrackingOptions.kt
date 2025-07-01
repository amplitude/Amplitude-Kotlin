package com.amplitude.android

class TrackingOptions {
    var disabledFields: MutableSet<String> = HashSet()

    fun disableAdid(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_ADID)
        return this
    }

    fun shouldTrackAdid(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_ADID)
    }

    fun disableAppSetId(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_APP_SET_ID)
        return this
    }

    fun shouldTrackAppSetId(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_APP_SET_ID)
    }

    fun disableCarrier(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_CARRIER)
        return this
    }

    fun shouldTrackCarrier(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_CARRIER)
    }

    fun disableCity(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_CITY)
        return this
    }

    fun shouldTrackCity(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_CITY)
    }

    fun disableCountry(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_COUNTRY)
        return this
    }

    fun shouldTrackCountry(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_COUNTRY)
    }

    fun disableDeviceBrand(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_DEVICE_BRAND)
        return this
    }

    fun shouldTrackDeviceBrand(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_DEVICE_BRAND)
    }

    fun disableDeviceManufacturer(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_DEVICE_MANUFACTURER)
        return this
    }

    fun shouldTrackDeviceManufacturer(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_DEVICE_MANUFACTURER)
    }

    fun disableDeviceModel(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_DEVICE_MODEL)
        return this
    }

    fun shouldTrackDeviceModel(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_DEVICE_MODEL)
    }

    fun disableDma(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_DMA)
        return this
    }

    fun shouldTrackDma(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_DMA)
    }

    fun disableIpAddress(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_IP_ADDRESS)
        return this
    }

    fun shouldTrackIpAddress(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_IP_ADDRESS)
    }

    fun disableLanguage(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_LANGUAGE)
        return this
    }

    fun shouldTrackLanguage(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_LANGUAGE)
    }

    fun disableLatLng(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_LAT_LNG)
        return this
    }

    fun shouldTrackLatLng(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_LAT_LNG)
    }

    fun disableOsName(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_OS_NAME)
        return this
    }

    fun shouldTrackOsName(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_OS_NAME)
    }

    fun disableOsVersion(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_OS_VERSION)
        return this
    }

    fun shouldTrackOsVersion(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_OS_VERSION)
    }

    fun disableApiLevel(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_API_LEVEL)
        return this
    }

    fun shouldTrackApiLevel(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_API_LEVEL)
    }

    fun disablePlatform(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_PLATFORM)
        return this
    }

    fun shouldTrackPlatform(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_PLATFORM)
    }

    fun disableRegion(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_REGION)
        return this
    }

    fun shouldTrackRegion(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_REGION)
    }

    fun disableVersionName(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_VERSION_NAME)
        return this
    }

    fun shouldTrackVersionName(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_VERSION_NAME)
    }

    private fun disableTrackingField(field: String) {
        disabledFields.add(field)
    }

    private fun shouldTrackField(field: String): Boolean {
        return !disabledFields.contains(field)
    }

    fun mergeIn(other: TrackingOptions): TrackingOptions {
        for (key in other.disabledFields) {
            disableTrackingField(key)
        }
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true // self check
        }
        if (other == null) {
            return false // null check
        }
        if (javaClass != other.javaClass) {
            return false // type check and cast
        }
        val options = other as TrackingOptions
        return options.disabledFields == disabledFields
    }

    companion object {
        private val TAG = TrackingOptions::class.java.name
        const val AMP_TRACKING_OPTION_ADID = "adid"
        const val AMP_TRACKING_OPTION_APP_SET_ID = "app_set_id"
        const val AMP_TRACKING_OPTION_CARRIER = "carrier"
        const val AMP_TRACKING_OPTION_CITY = "city"
        const val AMP_TRACKING_OPTION_COUNTRY = "country"
        const val AMP_TRACKING_OPTION_DEVICE_BRAND = "device_brand"
        const val AMP_TRACKING_OPTION_DEVICE_MANUFACTURER = "device_manufacturer"
        const val AMP_TRACKING_OPTION_DEVICE_MODEL = "device_model"
        const val AMP_TRACKING_OPTION_DMA = "dma"
        const val AMP_TRACKING_OPTION_IP_ADDRESS = "ip_address"
        const val AMP_TRACKING_OPTION_LANGUAGE = "language"
        const val AMP_TRACKING_OPTION_LAT_LNG = "lat_lng"
        const val AMP_TRACKING_OPTION_OS_NAME = "os_name"
        const val AMP_TRACKING_OPTION_OS_VERSION = "os_version"
        const val AMP_TRACKING_OPTION_API_LEVEL = "api_level"
        const val AMP_TRACKING_OPTION_PLATFORM = "platform"
        const val AMP_TRACKING_OPTION_REGION = "region"
        const val AMP_TRACKING_OPTION_VERSION_NAME = "version_name"
        private val SERVER_SIDE_PROPERTIES =
            arrayOf<String>(
                AMP_TRACKING_OPTION_CITY,
                AMP_TRACKING_OPTION_COUNTRY,
                AMP_TRACKING_OPTION_DMA,
                AMP_TRACKING_OPTION_IP_ADDRESS,
                AMP_TRACKING_OPTION_LAT_LNG,
                AMP_TRACKING_OPTION_REGION,
            )
        private val COPPA_CONTROL_PROPERTIES =
            arrayOf<String>(
                AMP_TRACKING_OPTION_ADID,
                AMP_TRACKING_OPTION_CITY,
                AMP_TRACKING_OPTION_IP_ADDRESS,
                AMP_TRACKING_OPTION_LAT_LNG,
            )

        fun copyOf(other: TrackingOptions): TrackingOptions {
            val trackingOptions = TrackingOptions()
            for (key in other.disabledFields) {
                trackingOptions.disableTrackingField(key)
            }
            return trackingOptions
        }

        fun forCoppaControl(): TrackingOptions {
            val trackingOptions = TrackingOptions()
            for (key in COPPA_CONTROL_PROPERTIES) {
                trackingOptions.disableTrackingField(key)
            }
            return trackingOptions
        }
    }
}
