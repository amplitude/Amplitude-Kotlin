package com.amplitude.android.migration

import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.utilities.toBaseEvent
import org.json.JSONObject

/**
 * When switching the SDK from previous version to this version, remnant data might remain unsent in sqlite.
 * This migration:
 *      1. reads device/user id, events, identifies from sqlite tables
 *      2. converts the events and identifies to JsonObjects
 *      3. saves the device/user id, converted events and identifies to current storage
 *      4. deletes data from sqlite table
 */

class RemnantDataMigration(
    val amplitude: Amplitude,
) {
    companion object {
        const val DEVICE_ID_KEY = "device_id"
        const val USER_ID_KEY = "user_id"
    }

    lateinit var databaseStorage: DatabaseStorage

    suspend fun execute() {
        databaseStorage = DatabaseStorageProvider.getStorage(amplitude)

        val firstRunSinceUpgrade = amplitude.storage.read(Storage.Constants.LAST_EVENT_TIME)?.toLongOrNull() == null

        // WARNING: We don't migrate session data as we want to reset on a new app install
        moveDeviceAndUserId()

        if (firstRunSinceUpgrade) {
            moveInterceptedIdentifies()
            moveIdentifies()
        }
        moveEvents()
    }

    private fun moveDeviceAndUserId() {
        try {
            val deviceId = databaseStorage.getValue(DEVICE_ID_KEY)
            val userId = databaseStorage.getValue(USER_ID_KEY)
            if (deviceId == null && userId == null) {
                return
            }

            val currentIdentity = amplitude.identityStorage.load()

            if (currentIdentity.deviceId == null && deviceId != null) {
                amplitude.identityStorage.saveDeviceId(deviceId)
            }

            if (currentIdentity.userId == null && userId != null) {
                amplitude.identityStorage.saveUserId(userId)
            }
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "device/user id migration failed: ${e.message}"
            )
        }
    }

    private suspend fun moveEvents() {
        try {
            val remnantEvents = databaseStorage.readEventsContent()

            for (event in remnantEvents) {
                moveEvent(event, amplitude.storage, databaseStorage::removeEvent)
            }
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "events migration failed: ${e.message}"
            )
        }
    }

    private suspend fun moveIdentifies() {
        try {
            val remnantIdentifies = databaseStorage.readIdentifiesContent()

            for (event in remnantIdentifies) {
                moveEvent(event, amplitude.storage, databaseStorage::removeIdentify)
            }
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "identifies migration failed: ${e.message}"
            )
        }
    }

    private suspend fun moveInterceptedIdentifies() {
        try {
            val remnantIdentifies = databaseStorage.readInterceptedIdentifiesContent()

            for (event in remnantIdentifies) {
                moveEvent(event, amplitude.identifyInterceptStorage, databaseStorage::removeInterceptedIdentify)
            }
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "intercepted identifies migration failed: ${e.message}"
            )
        }
    }

    private suspend fun moveEvent(event: JSONObject, destinationStorage: Storage, removeFromSource: (rowId: Long) -> Unit) {
        try {
            val rowId = convertLegacyEvent(event)
            destinationStorage.writeEvent(event.toBaseEvent())
            removeFromSource(rowId)
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "event migration failed: ${e.message}"
            )
        }
    }

    private fun convertLegacyEvent(event: JSONObject): Long {
        val rowId = event.getLong(DatabaseConstants.ROW_ID_FIELD)
        event.put("event_id", rowId)

        event.optJSONObject("library") ?.let { library ->
            event.put("library", "${library.getString("name")}/${library.getString("version")}")
        }

        event.opt("timestamp") ?.let { timestamp ->
            event.put("time", timestamp)
        }

        event.opt("uuid") ?.let { uuid ->
            event.put("insert_id", uuid)
        }

        event.optJSONObject("api_properties") ?.let { apiProperties ->
            apiProperties.opt("androidADID") ?.let { event.put("adid", it) }
            apiProperties.opt("android_app_set_id") ?.let { event.put("android_app_set_id", it) }

            apiProperties.opt("productId") ?.let { event.put("productId", it) }
            apiProperties.opt("quantity") ?.let { event.put("quantity", it) }
            apiProperties.opt("price") ?.let { event.put("price", it) }

            apiProperties.optJSONObject("location") ?.let { location ->
                location.opt("lat") ?.let { event.put("location_lat", it) }
                location.opt("lng") ?.let { event.put("location_lng", it) }
            }
        }

        event.opt("\$productId") ?.let {
            event.put("productId", it)
        }
        event.opt("\$quantity") ?.let {
            event.put("quantity", it)
        }
        event.opt("\$price") ?.let {
            event.put("price", it)
        }
        event.opt("\$revenueType") ?.let {
            event.put("revenueType", it)
        }

        return rowId
    }
}
