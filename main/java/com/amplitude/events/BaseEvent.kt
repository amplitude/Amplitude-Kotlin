package com.amplitude.events

import org.json.JSONObject

open class BaseEvent : EventOptions() {
    lateinit var eventType: String
    var eventProperties: JSONObject? = null
    var userProperties: JSONObject? = null
    var groups:JSONObject? = null
    var groupProperties: JSONObject? = null
}
