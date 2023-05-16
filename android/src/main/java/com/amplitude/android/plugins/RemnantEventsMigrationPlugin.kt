package com.amplitude.android.plugins

import com.amplitude.android.utilities.DatabaseConstants
import com.amplitude.android.utilities.DatabaseStorage
import com.amplitude.android.utilities.DatabaseStorageProvider
import com.amplitude.common.android.LogcatLogger
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.optionalJSONObject
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/**
 * When switching the SDK from previous version to this version, remnant events might remain unsent in sqlite.
 * This plugin:
 *      1. reads the events from sqlite table
 *      2. convert events to JsonObjects
 *      3. save events to current storage
 *      4. delete events from sqlite table
 */

const val DEVICE_ID_KEY = "device_id"
const val USER_ID_KEY = "user_id"

class RemnantEventsMigrationPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Utility
    override lateinit var amplitude: Amplitude
    lateinit var databaseStorage: DatabaseStorage

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)

        runBlocking {
            amplitude.isBuilt.await()
            databaseStorage = DatabaseStorageProvider().getStorage(amplitude)
            moveDeviceAndUserId()
            moveInterceptedIdentifies()
            moveIdentifies()
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

            val editor = amplitude.idContainer.identityManager.editIdentity()
            if (deviceId != null) {
                editor.setDeviceId(deviceId)
            }
            if (userId != null) {
                editor.setUserId(userId)
            }

            editor.commit()

            if (deviceId != null) {
                databaseStorage.removeValue(DEVICE_ID_KEY)
            }
            if (userId != null) {
                databaseStorage.removeValue(USER_ID_KEY)
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
                val rowId = moveEvent(event, amplitude.storage)
                databaseStorage.removeEvent(rowId)
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
                val rowId = moveEvent(event, amplitude.storage)
                databaseStorage.removeIdentify(rowId)
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
                val rowId = moveEvent(event, amplitude.identifyInterceptStorage)
                databaseStorage.removeInterceptedIdentify(rowId)
            }
        } catch (e: Exception) {
            LogcatLogger.logger.error(
                "identifies migration failed: ${e.message}"
            )
        }
    }

    private suspend fun moveEvent(event: JSONObject, storage: Storage): Long {
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

        storage.writeEvent(event)
        return rowId
    }
}
