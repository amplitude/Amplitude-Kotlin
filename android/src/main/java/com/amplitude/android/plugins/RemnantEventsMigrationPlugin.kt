package com.amplitude.android.plugins

import com.amplitude.android.Configuration
import com.amplitude.android.utilities.DatabaseStorageProvider
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.Plan
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.optionalJSONObject
import com.amplitude.core.utilities.optionalString
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

/**
 * When switching the SDK from previous version to this version, remnant events might remain unsent in sqlite.
 * This plugin:
 *      1. reads the events from sqlite table
 *      2. convert events to BaseEvent
 *      3. track events with this SDK native method
 *      4. delete events from sqlite table
 */
class RemnantEventsMigrationPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    private lateinit var contextProvider: AndroidContextProvider

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        val configuration = amplitude.configuration as Configuration
        contextProvider =
            AndroidContextProvider(configuration.context, configuration.locationListening)

        val databaseStorage = DatabaseStorageProvider().getStorage(amplitude)
        try {
            val remnantEvents = databaseStorage.readEventsContent()

            for (event in remnantEvents) {
                val baseEvent = event.toBaseEvent()
                amplitude.track(baseEvent)
                val eventId = baseEvent.eventId
                if (eventId != null) {
                    databaseStorage.removeEvent(eventId)
                }
            }
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "events migration failed: ${e.message}"
            )
        }
    }
}

// Copy from core/src/main/java/com/amplitude/core/utilities/JSON.kt with minor changes.
// To avoid unexpected results if open this extension to wide permission.
internal fun JSONObject.toBaseEvent(): BaseEvent {
    val event = BaseEvent()
    event.eventType = this.getString("event_type")
    event.userId = this.optionalString("user_id", null)
    event.deviceId = this.optionalString("device_id", null)
    event.timestamp = if (this.has("timestamp")) this.getLong("timestamp") else null // name changed
    event.eventProperties =
        this.optionalJSONObject("event_properties", null)?.toMapObj()?.toMutableMap()
    event.userProperties =
        this.optionalJSONObject("user_properties", null)?.toMapObj()?.toMutableMap()
    event.groups = this.optionalJSONObject("groups", null)?.toMapObj()?.toMutableMap()
    event.groupProperties =
        this.optionalJSONObject("group_properties", null)?.toMapObj()?.toMutableMap()
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
    event.appSetId = this.optionalString("android_app_set_id", null)
    event.eventId = if (this.has("event_id")) this.getLong("event_id") else null
    event.sessionId = this.getLong("session_id")
    event.insertId = this.optionalString("uuid", null) // name change
    event.library = if (this.has("library")) this.getString("library") else null
    event.partnerId = this.optionalString("partner_id", null)
    event.plan = if (this.has("plan")) Plan.fromJSONObject(this.getJSONObject("plan")) else null
    return event
}

// Copy from core/src/main/java/com/amplitude/core/utilities/JSON.kt.
// To avoid unexpected results if open this extension to wide permission.
private fun JSONObject.toMapObj(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    for (key in this.keys()) {
        map[key] = this[key].fromJSON()
    }
    return map
}

// Copy from core/src/main/java/com/amplitude/core/utilities/JSON.kt.
// To avoid unexpected results if open this extension to wide permission.
private fun Any?.fromJSON(): Any? {
    return when (this) {
        is JSONObject -> this.toMapObj()
        is JSONArray -> this.toListObj()
        // org.json uses BigDecimal for doubles and floats; normalize to double
        // to make testing for equality easier.
        is BigDecimal -> this.toDouble()
        JSONObject.NULL -> null
        else -> this
    }
}

// Copy from core/src/main/java/com/amplitude/core/utilities/JSON.kt.
// To avoid unexpected results if open this extension to wide permission.
private fun JSONArray.toListObj(): List<Any?> {
    val list = mutableListOf<Any?>()
    for (i in 0 until this.length()) {
        val value = this[i].fromJSON()
        list.add(value)
    }
    return list
}
