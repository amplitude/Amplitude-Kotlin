package com.amplitude.core.utilities

import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.Plan
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
        eventJSON.addValue("event_properties", truncate(event.eventProperties.toJSONObject()))
        eventJSON.addValue("user_properties", truncate(event.userProperties.toJSONObject()))
        eventJSON.addValue("groups", truncate(event.groups.toJSONObject()))
        eventJSON.addValue("group_properties", truncate(event.groupProperties.toJSONObject()))
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
        eventJSON.addValue("android_app_set_id", event.appSetId)
        event.plan?. let {
            eventJSON.put("plan", it.toJSONObject())
        }
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
    event.userId = this.optionalString("user_id", null)
    event.deviceId = this.optionalString("device_id", null)
    event.timestamp = if (this.has("time")) this.getLong("time") else null
    event.eventProperties = this.optionalJSONObject("event_properties", null)?.let { it.toMapObj().toMutableMap() }
    event.userProperties = this.optionalJSONObject("user_properties", null)?.let { it.toMapObj().toMutableMap() }
    event.groups = this.optionalJSONObject("groups", null)?.let { it.toMapObj().toMutableMap() }
    event.groupProperties = this.optionalJSONObject("group_properties", null)?.let { it.toMapObj().toMutableMap() }
    event.appVersion = this.optionalString("app_version", null)
    event.platform = this.optionalString("platform", null)
    event.osName = this.optionalString("os_name", null)
    event.osVersion = this.optionalString("os_version", null)
    event.deviceBrand = this.optionalString("device_brand", null)
    event.deviceManufacturer = this.optionalString("device_manufacturer", null)
    event.deviceModel = this.optionalString("device_model", null)
    event.carrier = this.optionalString("carrier", null)
    event.country = this.optionalString("country", null)
    event.region = this.optionalString("region", null)
    event.city = this.optionalString("city", null)
    event.dma = this.optionalString("dma", null)
    event.language = this.optionalString("language", null)
    event.price = if (this.has("price")) this.getDouble("price") else null
    event.quantity = if (this.has("quantity")) this.getInt("quantity") else null
    event.revenue = if (this.has("revenue")) this.getDouble("revenue") else null
    event.productId = this.optionalString("productId", null)
    event.revenueType = this.optionalString("revenueType", null)
    event.locationLat = if (this.has("location_lat")) this.getDouble("location_lat") else null
    event.locationLng = if (this.has("location_lng")) this.getDouble("location_lng") else null
    event.ip = this.optionalString("ip", null)
    event.idfa = this.optionalString("idfa", null)
    event.idfv = this.optionalString("idfv", null)
    event.adid = this.optionalString("adid", null)
    event.androidId = this.optionalString("android_id", null)
    event.appSetId = this.optString("android_app_set_id", null)
    event.eventId = if (this.has("event_id")) this.getLong("event_id") else null
    event.sessionId = this.getLong("session_id")
    event.insertId = this.optionalString("insert_id", null)
    event.library = if (this.has("library")) this.getString("library") else null
    event.partnerId = this.optionalString("partner_id", null)
    event.plan = if (this.has("plan")) Plan.fromJSONObject(this.getJSONObject("plan")) else null
    return event
}

internal fun JSONArray.toEvents(): List<BaseEvent> {
    val events = mutableListOf<BaseEvent>()
    (0 until this.length()).forEach {
        events.add((this.getJSONObject(it)).toBaseEvent())
    }
    return events
}

internal fun JSONArray.split(): Pair<String, String> {
    val mid = this.length() / 2
    val firstHalf = JSONArray()
    val secondHalf = JSONArray()
    (0 until this.length()).forEach { index, ->
        if (index < mid) {
            firstHalf.put(this.getJSONObject(index))
        } else {
            secondHalf.put(this.getJSONObject(index))
        }
    }
    return Pair(firstHalf.toString(), secondHalf.toString())
}

internal fun JSONObject.addValue(key: String, value: Any?) {
    value?.let {
        this.put(key, value)
    }
}

inline fun JSONObject.optionalJSONObject(key: String, defaultValue: JSONObject?): JSONObject? {
    if (this.has(key)) {
        return this.getJSONObject(key)
    }
    return defaultValue
}

inline fun JSONObject.optionalString(key: String, defaultValue: String?): String? {
    if (this.has(key)) {
        return this.getString(key)
    }
    return defaultValue
}
