package com.amplitude.id

interface IdentityStorage {
    fun load(): Identity

    fun saveUserId(userId: String?)

    fun saveDeviceId(deviceId: String?)

    fun delete()
}

interface IdentityStorageProvider {
    fun getIdentityStorage(configuration: IdentityConfiguration): IdentityStorage
}
