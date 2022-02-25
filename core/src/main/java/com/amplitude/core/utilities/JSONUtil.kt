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
        eventJSON.put("user_id", replaceWithJSONNull(event.userId))
        eventJSON.put("device_id", replaceWithJSONNull(event.deviceId))
        eventJSON.put("time", replaceWithJSONNull(event.timestamp))
        eventJSON.put("event_properties", truncate(event.eventProperties))
        eventJSON.put("user_properties", truncate(event.userProperties))
        eventJSON.put("groups", truncate(event.groups))
        eventJSON.put("app_version", replaceWithJSONNull(event.appVersion))
        eventJSON.put("platform", replaceWithJSONNull(event.platform))
        eventJSON.put("os_name", replaceWithJSONNull(event.osName))
        eventJSON.put("os_version", replaceWithJSONNull(event.osVersion))
        eventJSON.put("device_brand", replaceWithJSONNull(event.deviceBrand))
        eventJSON.put("device_manufacturer", replaceWithJSONNull(event.deviceManufacturer))
        eventJSON.put("device_model", replaceWithJSONNull(event.deviceModel))
        eventJSON.put("carrier", replaceWithJSONNull(event.carrier))
        eventJSON.put("country", replaceWithJSONNull(event.country))
        eventJSON.put("region", replaceWithJSONNull(event.region))
        eventJSON.put("city", replaceWithJSONNull(event.city))
        eventJSON.put("dma", replaceWithJSONNull(event.dma))
        eventJSON.put("language", replaceWithJSONNull(event.language))
        eventJSON.put("price", replaceWithJSONNull(event.price))
        eventJSON.put("quantity", replaceWithJSONNull(event.quantity))
        eventJSON.put("revenue", replaceWithJSONNull(event.revenue))
        eventJSON.put("productId", replaceWithJSONNull(event.productId))
        eventJSON.put("revenueType", replaceWithJSONNull(event.revenueType))
        eventJSON.put("location_lat", replaceWithJSONNull(event.locationLat))
        eventJSON.put("location_lng", replaceWithJSONNull(event.locationLng))
        eventJSON.put("ip", replaceWithJSONNull(event.ip))
        eventJSON.put("idfa", replaceWithJSONNull(event.idfa))
        eventJSON.put("idfv", replaceWithJSONNull(event.idfv))
        eventJSON.put("adid", replaceWithJSONNull(event.idfv))
        eventJSON.put("android_id", replaceWithJSONNull(event.androidId))
        eventJSON.put("event_id", replaceWithJSONNull(event.eventId))
        eventJSON.put("session_id", replaceWithJSONNull(event.sessionId))
        eventJSON.put("insert_id", replaceWithJSONNull(event.insertId))
        eventJSON.put("library", replaceWithJSONNull(event.library))
        return eventJSON
    }

    fun eventToString(event: BaseEvent): String {
        return eventToJsonObject(event).toString()
    }

    fun eventsToString(events: List<BaseEvent>): List<String> {
        if (events.isEmpty()) {
            return listOf("")
        }
        val eventsArray = JSONArray()
        for (event in events) {
            eventsArray.put(eventToJsonObject(event))
        }
        return listOf(eventsArray.toString())
    }

    private fun replaceWithJSONNull(obj: Any?): Any? {
        return obj ?: JSONObject.NULL
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
                    ("JSON parsing error. Too long (>"
                            + Constants.MAX_STRING_LENGTH
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