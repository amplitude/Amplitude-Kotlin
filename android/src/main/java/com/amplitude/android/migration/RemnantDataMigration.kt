package com.amplitude.android.migration

import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.platform.Initializer
import com.amplitude.core.utilities.optionalJSONObject
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * When switching the SDK from previous version to this version, remnant data might remain unsent in sqlite.
 * This plugin:
 *      1. reads device/user id, events, identifies from sqlite tables
 *      2. converts the events and identifies to JsonObjects
 *      3. saves the device/user id, converted events and identifies to current storage
 *      4. deletes data from sqlite table
 */

class RemnantDataMigration : Initializer {
    companion object {
        const val DEVICE_ID_KEY = "device_id"
        const val USER_ID_KEY = "user_id"
        const val LAST_EVENT_TIME_KEY = "last_event_time"
        const val LAST_EVENT_ID_KEY = "last_event_id"
        const val PREVIOUS_SESSION_ID_KEY = "previous_session_id"
    }

    lateinit var amplitude: Amplitude
    lateinit var databaseStorage: DatabaseStorage

    override fun execute(amplitude: Amplitude) {
        this.amplitude = amplitude
        runBlocking {
            databaseStorage = DatabaseStorageProvider().getStorage(amplitude)

            val firstRunSinceUpgrade = amplitude.storage.read(Storage.Constants.LAST_EVENT_TIME)?.toLongOrNull() == null

            moveDeviceAndUserId()
            moveSessionData()

            if (firstRunSinceUpgrade) {
                moveInterceptedIdentifies()
                moveIdentifies()
            }
            moveEvents()
        }
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

    private suspend fun moveSessionData() {
        try {
            val currentSessionId = amplitude.storage.read(Storage.Constants.PREVIOUS_SESSION_ID)?.toLongOrNull()
            val currentLastEventTime = amplitude.storage.read(Storage.Constants.LAST_EVENT_TIME)?.toLongOrNull()
            val currentLastEventId = amplitude.storage.read(Storage.Constants.LAST_EVENT_ID)?.toLongOrNull()

            val previousSessionId = databaseStorage.getLongValue(PREVIOUS_SESSION_ID_KEY)
            val lastEventTime = databaseStorage.getLongValue(LAST_EVENT_TIME_KEY)
            val lastEventId = databaseStorage.getLongValue(LAST_EVENT_ID_KEY)

            if (currentSessionId == null && previousSessionId != null) {
                amplitude.storage.write(Storage.Constants.PREVIOUS_SESSION_ID, previousSessionId.toString())
                databaseStorage.removeLongValue(PREVIOUS_SESSION_ID_KEY)
            }

            if (currentLastEventTime == null && lastEventTime != null) {
                amplitude.storage.write(Storage.Constants.LAST_EVENT_TIME, lastEventTime.toString())
                databaseStorage.removeLongValue(LAST_EVENT_TIME_KEY)
            }

            if (currentLastEventId == null && lastEventId != null) {
                amplitude.storage.write(Storage.Constants.LAST_EVENT_ID, lastEventId.toString())
                databaseStorage.removeLongValue(LAST_EVENT_ID_KEY)
            }
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "session data migration failed: ${e.message}"
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
            val rowId = event.getLong(DatabaseConstants.ROW_ID_FIELD)
            event.put("event_id", rowId)
            event.remove(DatabaseConstants.ROW_ID_FIELD)

            val library = event.optionalJSONObject("library", null)
            if (library != null) {
                event.put("library", "${library.getString("name")}/${library.getString("version")}")
            }

            val timestamp = event.getLong("timestamp")
            event.put("time", timestamp)

            val apiProperties = event.optionalJSONObject("api_properties", null)
            if (apiProperties != null) {
                if (apiProperties.has("androidADID")) {
                    val adid = apiProperties.getString("androidADID")
                    event.put("adid", adid)
                }
                if (apiProperties.has("android_app_set_id")) {
                    val appSetId = apiProperties.getString("android_app_set_id")
                    event.put("android_app_set_id", appSetId)
                }
            }

            destinationStorage.writeEvent(event)
            removeFromSource(rowId)
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "event migration failed: ${e.message}"
            )
        }
    }
}
