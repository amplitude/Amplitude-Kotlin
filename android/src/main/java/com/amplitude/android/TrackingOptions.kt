package com.amplitude.android

public class TrackingOptions {
    public var disabledFields: MutableSet<String> = HashSet()

    public fun disableAdid(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_ADID)
        return this
    }

    public fun shouldTrackAdid(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_ADID)
    }

    public fun disableAppSetId(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_APP_SET_ID)
        return this
    }

    public fun shouldTrackAppSetId(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_APP_SET_ID)
    }

    public fun disableCarrier(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_CARRIER)
        return this
    }

    public fun shouldTrackCarrier(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_CARRIER)
    }

    public fun disableCity(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_CITY)
        return this
    }

    public fun shouldTrackCity(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_CITY)
    }

    public fun disableCountry(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_COUNTRY)
        return this
    }

    public fun shouldTrackCountry(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_COUNTRY)
    }

    public fun disableDeviceBrand(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_DEVICE_BRAND)
        return this
    }

    public fun shouldTrackDeviceBrand(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_DEVICE_BRAND)
    }

    public fun disableDeviceManufacturer(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_DEVICE_MANUFACTURER)
        return this
    }

    public fun shouldTrackDeviceManufacturer(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_DEVICE_MANUFACTURER)
    }

    public fun disableDeviceModel(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_DEVICE_MODEL)
        return this
    }

    public fun shouldTrackDeviceModel(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_DEVICE_MODEL)
    }

    public fun disableDma(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_DMA)
        return this
    }

    public fun shouldTrackDma(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_DMA)
    }

    public fun disableIpAddress(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_IP_ADDRESS)
        return this
    }

    public fun shouldTrackIpAddress(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_IP_ADDRESS)
    }

    public fun disableLanguage(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_LANGUAGE)
        return this
    }

    public fun shouldTrackLanguage(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_LANGUAGE)
    }

    public fun disableLatLng(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_LAT_LNG)
        return this
    }

    public fun shouldTrackLatLng(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_LAT_LNG)
    }

    public fun disableOsName(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_OS_NAME)
        return this
    }

    public fun shouldTrackOsName(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_OS_NAME)
    }

    public fun disableOsVersion(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_OS_VERSION)
        return this
    }

    public fun shouldTrackOsVersion(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_OS_VERSION)
    }

    public fun disableApiLevel(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_API_LEVEL)
        return this
    }

    public fun shouldTrackApiLevel(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_API_LEVEL)
    }

    public fun disablePlatform(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_PLATFORM)
        return this
    }

    public fun shouldTrackPlatform(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_PLATFORM)
    }

    public fun disableRegion(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_REGION)
        return this
    }

    public fun shouldTrackRegion(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_REGION)
    }

    public fun disableVersionName(): TrackingOptions {
        disableTrackingField(AMP_TRACKING_OPTION_VERSION_NAME)
        return this
    }

    public fun shouldTrackVersionName(): Boolean {
        return shouldTrackField(AMP_TRACKING_OPTION_VERSION_NAME)
    }

    private fun disableTrackingField(field: String) {
        disabledFields.add(field)
    }

    private fun shouldTrackField(field: String): Boolean {
        return !disabledFields.contains(field)
    }

    public fun mergeIn(other: TrackingOptions): TrackingOptions {
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

    public companion object {
        private val TAG = TrackingOptions::class.java.name
        public const val AMP_TRACKING_OPTION_ADID: String = "adid"
        public const val AMP_TRACKING_OPTION_APP_SET_ID: String = "app_set_id"
        public const val AMP_TRACKING_OPTION_CARRIER: String = "carrier"
        public const val AMP_TRACKING_OPTION_CITY: String = "city"
        public const val AMP_TRACKING_OPTION_COUNTRY: String = "country"
        public const val AMP_TRACKING_OPTION_DEVICE_BRAND: String = "device_brand"
        public const val AMP_TRACKING_OPTION_DEVICE_MANUFACTURER: String = "device_manufacturer"
        public const val AMP_TRACKING_OPTION_DEVICE_MODEL: String = "device_model"
        public const val AMP_TRACKING_OPTION_DMA: String = "dma"
        public const val AMP_TRACKING_OPTION_IP_ADDRESS: String = "ip_address"
        public const val AMP_TRACKING_OPTION_LANGUAGE: String = "language"
        public const val AMP_TRACKING_OPTION_LAT_LNG: String = "lat_lng"
        public const val AMP_TRACKING_OPTION_OS_NAME: String = "os_name"
        public const val AMP_TRACKING_OPTION_OS_VERSION: String = "os_version"
        public const val AMP_TRACKING_OPTION_API_LEVEL: String = "api_level"
        public const val AMP_TRACKING_OPTION_PLATFORM: String = "platform"
        public const val AMP_TRACKING_OPTION_REGION: String = "region"
        public const val AMP_TRACKING_OPTION_VERSION_NAME: String = "version_name"
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

        public fun copyOf(other: TrackingOptions): TrackingOptions {
            val trackingOptions = TrackingOptions()
            for (key in other.disabledFields) {
                trackingOptions.disableTrackingField(key)
            }
            return trackingOptions
        }

        public fun forCoppaControl(): TrackingOptions {
            val trackingOptions = TrackingOptions()
            for (key in COPPA_CONTROL_PROPERTIES) {
                trackingOptions.disableTrackingField(key)
            }
            return trackingOptions
        }
    }
}
