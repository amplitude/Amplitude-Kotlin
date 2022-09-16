package com.amplitude.core.events

/**
 * BaseEvent for SDK
 */
open class BaseEvent : EventOptions() {
    open lateinit var eventType: String
    var eventProperties: MutableMap<String, Any?>? = null
    var userProperties: MutableMap<String, Any?>? = null
    var groups: MutableMap<String, Any?>? = null
    var groupProperties: MutableMap<String, Any?>? = null

    fun mergeEventOptions(options: EventOptions) {
        userId ?: let { userId = options.userId }
        deviceId ?: let { deviceId = options.deviceId }
        timestamp ?: let { timestamp = options.timestamp }
        eventId ?: let { eventId = options.eventId }
        insertId ?: let { insertId = options.insertId }
        locationLat ?: let { locationLat = options.locationLat }
        locationLng ?: let { locationLng = options.locationLng }
        appVersion ?: let { appVersion = options.appVersion }
        versionName ?: let { versionName = options.versionName }
        platform ?: let { platform = options.platform }
        osName ?: let { osName = options.osName }
        osVersion ?: let { osVersion = options.osVersion }
        deviceBrand ?: let { deviceBrand = options.deviceBrand }
        deviceManufacturer ?: let { deviceManufacturer = options.deviceManufacturer }
        deviceModel ?: let { deviceModel = options.deviceModel }
        carrier ?: let { carrier = options.carrier }
        country ?: let { country = options.country }
        region ?: let { region = options.region }
        city ?: let { city = options.city }
        dma ?: let { dma = options.dma }
        idfa ?: let { idfa = options.idfa }
        idfv ?: let { idfv = options.idfv }
        adid ?: let { adid = options.adid }
        appSetId ?: let { appSetId = options.appSetId }
        androidId ?: let { androidId = options.androidId }
        language ?: let { language = options.language }
        library ?: let { library = options.library }
        ip ?: let { ip = options.ip }
        plan ?: let { plan = options.plan }
        ingestionMetadata ?: let { ingestionMetadata = options.ingestionMetadata }
        revenue ?: let { revenue = options.revenue }
        price ?: let { price = options.price }
        quantity ?: let { quantity = options.quantity }
        productId ?: let { productId = options.productId }
        revenueType ?: let { revenueType = options.revenueType }
        extra ?: let { extra = options.extra }
        callback ?: let { callback = options.callback }
        partnerId ?: let { partnerId = options.partnerId }
    }

    /**
     * Check if event is valid
     */
    open fun isValid(): Boolean {
        return userId != null || deviceId != null
    }
}
