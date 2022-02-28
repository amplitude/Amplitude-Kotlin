package com.amplitude.id

import com.amplitude.id.utilities.PropertiesFile
import com.amplitude.id.utilities.createDirectory
import java.io.File

class FileIdentityStorage(val apiKey: String): IdentityStorage {
    lateinit var identityStore: IdentityStore
    private val propertiesFile: PropertiesFile

    companion object {
        const val STORAGE_PREFIX = "amplitude-identity"
        const val USER_ID_KEY = "user_id"
        const val DEVICE_ID_KEY = "device_id"
    }

    init {
        val storageDirectory = File("/tmp/amplitude-identity/$apiKey")
        createDirectory(storageDirectory)
        propertiesFile = PropertiesFile(storageDirectory, apiKey, STORAGE_PREFIX)
    }

    override fun setup(identityStore: IdentityStore) {
        this.identityStore = identityStore
        identityStore.addIdentityListener(FileIdentityListener(this))
        load()
    }

    private fun load() {
        val userId = propertiesFile.getString(USER_ID_KEY, null)
        val deviceId = propertiesFile.getString(DEVICE_ID_KEY, null)
        identityStore.setIdentity(Identity(userId, deviceId), IdentityUpdateType.Initialized)
    }

    override fun saveUserId(userId: String?) {
        propertiesFile.putString(USER_ID_KEY, userId ?: "")
    }

    override fun saveDeviceId(deviceId: String?) {
        propertiesFile.putString(DEVICE_ID_KEY, deviceId ?: "")
    }
}

class FileIdentityStorageProvider: IdentityStorageProvider {
    override fun getIdentityStorage(apiKey: String): IdentityStorage {
        return FileIdentityStorage(apiKey)
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
