package com.amplitude.events

open class EventOptions {
    var locationLat: Double? = null
    var locationLng: Double? = null
    var appVersion: String? = null
    var versionName: String? = null
    var platform: String? = null
    var osName: String? = null
    var deviceBrand: String? = null
    var deviceManufacturer: String? = null
    var deviceModel: String? = null
    var carrier: String? = null
    var country: String? = null
    var region: String? = null
    var city: String? = null
    var dms: String? = null
    var idfa: String? = null
    var idfv: String? = null
    var adid: String? = null
    var appSetId: String? = null
    var androidId: String? = null
    var language: String? = null
    var library: String? = null
    var ip: String? = null
    var plan: Plan? = null
    var extra: Map<String, Any>? = null
}
