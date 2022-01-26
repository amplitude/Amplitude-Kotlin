package com.amplitude.kotlin.events

import org.json.JSONObject
import java.util.UUID

open class BaseEvent {
    open lateinit var eventType: String
    lateinit var usetId: String
    lateinit var deviceId: String
    var timestamp = System.currentTimeMillis()
    var locationLat: Double = 0.0
    var locationLng: Double = 0.0
    lateinit var appVersion: String
    lateinit var versionName: String
    lateinit var platform: String
    lateinit var osName: String
    lateinit var deviceBrand: String
    lateinit var deviceManufacturer: String
    lateinit var deviceModel: String
    lateinit var carrier: String
    lateinit var country: String
    lateinit var region: String
    lateinit var city: String
    lateinit var dms: String
    lateinit var idfa: String
    lateinit var idfv: String
    lateinit var adid: String
    lateinit var appSetId: String
    lateinit var androidId: String
    lateinit var language: String
    lateinit var library: String
    lateinit var ip: String
    lateinit var eventProperties: JSONObject
    lateinit var userProperties: JSONObject
    var eventId: Int = 0
    var sessionId: Long = -1
    var insertId = UUID.randomUUID().toString()
    lateinit var groups:JSONObject
    lateinit var groupProperties: JSONObject
}