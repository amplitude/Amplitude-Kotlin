package com.amplitude.id

import com.amplitude.id.utilities.PropertiesFile
import com.amplitude.id.utilities.createDirectory
import java.io.File

class FileIdentityStorage(val configuration: IdConfiguration): IdentityStorage {
    lateinit var identityStore: IdentityManager
    private val propertiesFile: PropertiesFile

    companion object {
        const val STORAGE_PREFIX = "amplitude-identity"
        const val USER_ID_KEY = "user_id"
        const val DEVICE_ID_KEY = "device_id"
        const val API_KEY = "api_key"
        const val EXPERIMENT_API_KEY = "experiment_api_key"
    }

    init {
        val instanceName = configuration.instanceName
        val storageDirectory = File("/tmp/amplitude-identity/$instanceName}")
        createDirectory(storageDirectory)
        propertiesFile = PropertiesFile(storageDirectory, instanceName, STORAGE_PREFIX)
    }

    override fun setup(identityManager: IdentityManager) {
        this.identityStore = identityManager
        identityManager.addIdentityListener(FileIdentityListener(this))
        load()
    }

    override fun saveUserId(userId: String?) {
        propertiesFile.putString(USER_ID_KEY, userId ?: "")
    }

    override fun saveDeviceId(deviceId: String?) {
        propertiesFile.putString(DEVICE_ID_KEY, deviceId ?: "")
    }

    private fun load() {
        safetyCheck()
        val userId = propertiesFile.getString(USER_ID_KEY, null)
        val deviceId = propertiesFile.getString(DEVICE_ID_KEY, null)
        identityStore.setIdentity(Identity(userId, deviceId), IdentityUpdateType.Initialized)
    }

    private fun safetyCheck() {
        if (safeForKey(API_KEY, configuration.apiKey) && safeForKey(EXPERIMENT_API_KEY, configuration.experimentApiKey)) {
            return
        }
        // api key not matching saved one, clear current value
        propertiesFile.remove(listOf(USER_ID_KEY, DEVICE_ID_KEY, API_KEY, EXPERIMENT_API_KEY))
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
}

class FileIdentityStorageProvider: IdentityStorageProvider {
    override fun getIdentityStorage(configuration: IdConfiguration): IdentityStorage {
        return FileIdentityStorage(configuration)
    }
}

class FileIdentityListener(private val identityStorage: FileIdentityStorage) : IdentityListener {

    override fun onUserIdChange(userId: String?) {
        identityStorage.saveUserId(userId)
    }

    override fun onDeviceIdChange(deviceId: String?) {
        identityStorage.saveDeviceId(deviceId)
    }

    override fun onIdentityChanged(identity: Identity, updateType: IdentityUpdateType) {

    }
}
