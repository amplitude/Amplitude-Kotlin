package com.amplitude.core.utilities

import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object JSONUtil {

    fun eventToJsonObject(event: BaseEvent): JSONObject {
        val eventJSON = JSONObject()
        eventJSON.put("event_type", event.eventType)
        eventJSON.addValue("user_id", event.userId)
        eventJSON.addValue("device_id", event.deviceId)
        eventJSON.addValue("time", event.timestamp)
        eventJSON.addValue("event_properties", truncate(event.eventProperties))
        eventJSON.addValue("user_properties", truncate(event.userProperties))
        eventJSON.addValue("groups", truncate(event.groups))
        eventJSON.addValue("app_version", event.appVersion)
        eventJSON.addValue("platform", event.platform)
        eventJSON.addValue("os_name", event.osName)
        eventJSON.addValue("os_version", event.osVersion)
        eventJSON.addValue("device_brand", event.deviceBrand)
        eventJSON.addValue("device_manufacturer", event.deviceManufacturer)
        eventJSON.addValue("device_model", event.deviceModel)
        eventJSON.addValue("carrier", event.carrier)
        eventJSON.addValue("country", event.country)
        eventJSON.addValue("region", event.region)
        eventJSON.addValue("city", event.city)
        eventJSON.addValue("dma", event.dma)
        eventJSON.addValue("language", event.language)
        eventJSON.addValue("price", event.price)
        eventJSON.addValue("quantity", event.quantity)
        eventJSON.addValue("revenue", event.revenue)
        eventJSON.addValue("productId", event.productId)
        eventJSON.addValue("revenueType", event.revenueType)
        eventJSON.addValue("location_lat", event.locationLat)
        eventJSON.addValue("location_lng", event.locationLng)
        eventJSON.addValue("ip", event.ip)
        eventJSON.addValue("idfa", event.idfa)
        eventJSON.addValue("idfv", event.idfv)
        eventJSON.addValue("adid", event.adid)
        eventJSON.addValue("android_id", event.androidId)
        eventJSON.addValue("event_id", event.eventId)
        eventJSON.addValue("session_id", event.sessionId)
        eventJSON.addValue("insert_id", event.insertId)
        eventJSON.addValue("library", event.library)
        eventJSON.addValue("partner_id", event.partnerId)
        return eventJSON
    }

    fun eventToString(event: BaseEvent): String {
        return eventToJsonObject(event).toString()
    }

    fun eventsToString(events: List<BaseEvent>): String {
        if (events.isEmpty()) {
            return ""
        }
        val eventsArray = JSONArray()
        for (event in events) {
            eventsArray.put(eventToJsonObject(event))
        }
        return eventsArray.toString()
    }

    private fun truncate(obj: JSONObject?): JSONObject? {
        if (obj == null) {
            return JSONObject()
        }
        if (obj.length() > Constants.MAX_PROPERTY_KEYS) {
            throw IllegalArgumentException("Too many properties (more than " + Constants.MAX_PROPERTY_KEYS.toString() + ") in JSON")
        }
        val keys: Iterator<*> = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next() as String
            try {
                val value = obj[key]
                if (value.javaClass == String::class.java) {
                    obj.put(key, truncate(value as String))
                } else if (value.javaClass == JSONObject::class.java) {
                    obj.put(key, truncate(value as JSONObject))
                } else if (value.javaClass == JSONArray::class.java) {
                    obj.put(key, truncate(value as JSONArray))
                }
            } catch (e: JSONException) {
                throw IllegalArgumentException(
                    (
                        "JSON parsing error. Too long (>" +
                            Constants.MAX_STRING_LENGTH
                        ) + " chars) or invalid JSON"
                )
            }
        }
        return obj
    }

    @Throws(JSONException::class)
    fun truncate(array: JSONArray?): JSONArray? {
        if (array == null) {
            return JSONArray()
        }
        for (i in 0 until array.length()) {
            val value = array[i]
            if ((value.javaClass == String::class.java)) {
                array.put(i, truncate(value as String))
            } else if ((value.javaClass == JSONObject::class.java)) {
                array.put(i, truncate(value as JSONObject))
            } else if ((value.javaClass == JSONArray::class.java)) {
                array.put(i, truncate(value as JSONArray))
            }
        }
        return array
    }

    private fun truncate(value: String): String? {
        return if (value.length <= Constants.MAX_STRING_LENGTH) value else value.substring(
            0,
            Constants.MAX_STRING_LENGTH
        )
    }
}

internal fun JSONObject.getStringWithDefault(key: String, defaultValue: String): String {
    if (this.has(key)) {
        return this.getString(key)
    }
    return defaultValue
}

internal fun JSONObject.collectIndices(): Set<Int> {
    val indices = mutableListOf<Int>()
    val fieldKeys: Iterator<String> = this.keys()
    while (fieldKeys.hasNext()) {
        val fieldKey = fieldKeys.next()
        val eventIndices: IntArray = this.getJSONArray(fieldKey).toIntArray()
        for (eventIndex in eventIndices) {
            indices.add(eventIndex)
        }
    }
    return indices.toSet()
}

internal fun JSONArray.toIntArray(): IntArray {
    val intArray = IntArray(this.length())
    for (i in intArray.indices) {
        intArray[i] = this.optInt(i)
    }
    return intArray
}

internal fun JSONObject.toBaseEvent(): BaseEvent {
    val event = BaseEvent()
    event.eventType = this.getString("event_type")
    event.userId = this.optString("user_id", null)
    event.deviceId = this.optString("device_id", null)
    event.timestamp = if (this.has("time")) this.getLong("time") else null
    event.eventProperties = this.optJSONObject("event_properties", null)
    event.userProperties = this.optJSONObject("user_properties", null)
    event.groups = this.optJSONObject("groups", null)
    event.appVersion = this.optString("app_version", null)
    event.platform = this.optString("platform", null)
    event.osName = this.optString("os_name", null)
    event.osVersion = this.optString("os_version", null)
    event.deviceBrand = this.optString("device_brand", null)
    event.deviceManufacturer = this.optString("device_manufacturer", null)
    event.deviceModel = this.optString("device_model", null)
    event.carrier = this.optString("carrier", null)
    event.country = this.optString("country", null)
    event.region = this.optString("region", null)
    event.city = this.optString("city", null)
    event.dma = this.optString("dma", null)
    event.language = this.optString("language", null)
    event.price = if (this.has("price")) this.getDouble("price") else null
    event.quantity = if (this.has("quantity")) this.getInt("quantity") else null
    event.revenue = if (this.has("revenue")) this.getDouble("revenue") else null
    event.productId = this.optString("productId", null)
    event.revenueType = this.optString("revenueType", null)
    event.locationLat = if (this.has("location_lat")) this.getDouble("location_lat") else null
    event.locationLng = if (this.has("location_lng")) this.getDouble("location_lng") else null
    event.ip = this.optString("ip", null)
    event.idfa = this.optString("idfa", null)
    event.idfv = this.optString("idfv", null)
    event.adid = this.optString("adid", null)
    event.androidId = this.optString("android_id", null)
    event.eventId = if (this.has("event_id")) this.getInt("event_id") else null
    event.sessionId = this.getLong("session_id")
    event.insertId = this.optString("insert_id", null)
    event.library = if (this.has("library")) this.getString("library") else null
    event.partnerId = this.optString("partner_id", null)
    return event
}

internal fun JSONArray.toEvents(): List<BaseEvent> {
    val events = mutableListOf<BaseEvent>()
    this.forEach {
        events.add((it as JSONObject).toBaseEvent())
    }
    return events
}

internal fun JSONArray.split(): Pair<String, String> {
    val mid = this.length() / 2
    val firstHalf = JSONArray()
    val secondHalf = JSONArray()
    this.forEachIndexed { index, obj ->
        if (index < mid) {
            firstHalf.put(obj)
        } else {
            secondHalf.put(obj)
        }
    }
    return Pair(firstHalf.toString(), secondHalf.toString())
}

internal fun JSONObject.addValue(key: String, value: Any?) {
    value?.let {
        this.put(key, value)
    }
}
