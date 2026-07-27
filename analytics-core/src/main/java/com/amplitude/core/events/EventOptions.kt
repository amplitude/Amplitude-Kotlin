package com.amplitude.core.events

import com.amplitude.core.EventCallBack

public open class EventOptions {
    public var userId: String? = null
    public var deviceId: String? = null
    public var timestamp: Long? = null
    public var eventId: Long? = null
    public var sessionId: Long? = null
    public var insertId: String? = null
    public var locationLat: Double? = null
    public var locationLng: Double? = null
    public var appVersion: String? = null
    public var versionName: String? = null
    public var platform: String? = null
    public var osName: String? = null
    public var osVersion: String? = null
    public var deviceBrand: String? = null
    public var deviceManufacturer: String? = null
    public var deviceModel: String? = null
    public var carrier: String? = null
    public var country: String? = null
    public var region: String? = null
    public var city: String? = null
    public var dma: String? = null
    public var idfa: String? = null
    public var idfv: String? = null
    public var adid: String? = null
    public var appSetId: String? = null
    public var androidId: String? = null
    public var language: String? = null
    public var library: String? = null
    public var ip: String? = null
    public var plan: Plan? = null
    public var ingestionMetadata: IngestionMetadata? = null
    public var revenue: Double? = null
    public var price: Double? = null
    public var quantity: Int? = null
    public var productId: String? = null
    public var revenueType: String? = null
    public var currency: String? = null
    public var extra: Map<String, Any>? = null
    public var callback: EventCallBack? = null
    public var partnerId: String? = null
    internal var attempts: Int = 0
}
