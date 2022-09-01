package com.amplitude.core.events

import com.amplitude.core.EventCallBack

open class EventOptions {
    var userId: String? = null
    var deviceId: String? = null
    var timestamp: Long? = null
    var eventId: Long? = null
    var sessionId: Long = -1
    var insertId: String? = null
    var locationLat: Double? = null
    var locationLng: Double? = null
    var appVersion: String? = null
    var versionName: String? = null
    var platform: String? = null
    var osName: String? = null
    var osVersion: String? = null
    var deviceBrand: String? = null
    var deviceManufacturer: String? = null
    var deviceModel: String? = null
    var carrier: String? = null
    var country: String? = null
    var region: String? = null
    var city: String? = null
    var dma: String? = null
    var idfa: String? = null
    var idfv: String? = null
    var adid: String? = null
    var appSetId: String? = null
    var androidId: String? = null
    var language: String? = null
    var library: String? = null
    var ip: String? = null
    var plan: Plan? = null
    var ingestionMetadata: IngestionMetadata? = null
    var revenue: Double? = null
    var price: Double? = null
    var quantity: Int? = null
    var productId: String? = null
    var revenueType: String? = null
    var extra: Map<String, Any>? = null
    var callback: EventCallBack? = null
    var partnerId: String? = null
    internal var attempts: Int = 0
}
