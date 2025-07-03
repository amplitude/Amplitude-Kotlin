package com.amplitude.core.events

import com.amplitude.core.platform.plugins.AnalyticsEvent

/**
 * BaseEvent for SDK
 */
open class BaseEvent : EventOptions(), AnalyticsEvent {
    override lateinit var eventType: String
    override var eventProperties: MutableMap<String, Any>? = null
    var userProperties: MutableMap<String, Any>? = null
    var groups: MutableMap<String, Any>? = null
    var groupProperties: MutableMap<String, Any>? = null

    fun mergeEventOptions(options: EventOptions) {
        options.userId?.let { userId = it }
        options.deviceId?.let { deviceId = it }
        options.timestamp?.let { timestamp = it }
        options.eventId?.let { eventId = it }
        options.insertId?.let { insertId = it }
        options.locationLat?.let { locationLat = it }
        options.locationLng?.let { locationLng = it }
        options.appVersion?.let { appVersion = it }
        options.versionName?.let { versionName = it }
        options.platform?.let { platform = it }
        options.osName?.let { osName = it }
        options.osVersion?.let { osVersion = it }
        options.deviceBrand?.let { deviceBrand = it }
        options.deviceManufacturer?.let { deviceManufacturer = it }
        options.deviceModel?.let { deviceModel = it }
        options.carrier?.let { carrier = it }
        options.country?.let { country = it }
        options.region?.let { region = it }
        options.city?.let { city = it }
        options.dma?.let { dma = it }
        options.idfa?.let { idfa = it }
        options.idfv?.let { idfv = it }
        options.adid?.let { adid = it }
        options.appSetId?.let { appSetId = it }
        options.androidId?.let { androidId = it }
        options.language?.let { language = it }
        options.library?.let { library = it }
        options.ip?.let { ip = it }
        options.plan?.let { plan = it }
        options.ingestionMetadata?.let { ingestionMetadata = it }
        options.revenue?.let { revenue = it }
        options.price?.let { price = it }
        options.quantity?.let { quantity = it }
        options.productId?.let { productId = it }
        options.revenueType?.let { revenueType = it }
        options.currency?.let { currency = it }
        options.extra?.let { extra = it }
        options.callback?.let { callback = it }
        options.partnerId?.let { partnerId = it }
        options.sessionId?.let { sessionId = it }
    }

    /**
     * Check if event is valid
     */
    open fun isValid(): Boolean {
        return userId != null || deviceId != null
    }

    fun setEventProperty(
        key: String,
        value: Any,
    ): BaseEvent {
        eventProperties = eventProperties ?: mutableMapOf()
        eventProperties?.put(key, value)
        return this
    }
}
