package com.amplitude.kotlin.events

import org.json.JSONObject

open class BaseEvent : EventOptions() {
    lateinit var eventType: String
    var userId: String? = null
    var deviceId: String? = null
    var timestamp: Long? = null
    var eventProperties: JSONObject? = null
    var userProperties: JSONObject? = null
    var eventId: Int? = null
    var sessionId: Long = -1
    var insertId: String? = null
    var groups:JSONObject? = null
    var groupProperties: JSONObject? = null
    var callback: ((BaseEvent) -> Unit)? = null
}