package com.amplitude.id

import com.amplitude.id.utilities.PropertiesFile
import com.amplitude.id.utilities.createDirectory
import java.io.File

class FileIdentityStorage(val configuration: IdentityConfiguration) : IdentityStorage {
    private val propertiesFile: PropertiesFile

    companion object {
        const val USER_ID_KEY = "user_id"
        const val DEVICE_ID_KEY = "device_id"
        const val API_KEY = "api_key"
        const val EXPERIMENT_API_KEY = "experiment_api_key"
    }

    init {
        val storageDirectory = configuration.storageDirectory
        createDirectory(storageDirectory)
        propertiesFile = PropertiesFile(storageDirectory, configuration.fileName, configuration.logger)
        propertiesFile.load()
        safetyCheck()
    }

    override fun saveUserId(userId: String?) {
        propertiesFile.putString(USER_ID_KEY, userId ?: "")
    }

    override fun saveDeviceId(deviceId: String?) {
        propertiesFile.putString(DEVICE_ID_KEY, deviceId ?: "")
    }

    override fun load(): Identity {
        val userId = propertiesFile.getString(USER_ID_KEY, null)
        val deviceId = propertiesFile.getString(DEVICE_ID_KEY, null)
        return Identity(userId, deviceId)
    }

    private fun safetyCheck() {
        if (!(
            safeForKey(API_KEY, configuration.apiKey) && safeForKey(
                    EXPERIMENT_API_KEY,
                    configuration.experimentApiKey
                )
            )
        ) {
            // api key not matching saved one, clear current value
            propertiesFile.remove(listOf(USER_ID_KEY, DEVICE_ID_KEY, API_KEY, EXPERIMENT_API_KEY))
        }
        configuration.apiKey?.let {
            propertiesFile.putString(API_KEY, it)
        }
        configuration.experimentApiKey?.let {
            propertiesFile.putString(EXPERIMENT_API_KEY, it)
        }
    }

    private fun safeForKey(apiKey: String, configValue: String?): Boolean {
        if (configValue == null) {
            return true
        }
        val savedApiKey = propertiesFile.getString(apiKey, null) ?: return true
        return savedApiKey == configValue
    }

    override fun delete() {
        propertiesFile.deletePropertiesFile()
    }
}

class FileIdentityStorageProvider : IdentityStorageProvider {
    override fun getIdentityStorage(configuration: IdentityConfiguration): IdentityStorage {
        return FileIdentityStorage(configuration)
    }
}
