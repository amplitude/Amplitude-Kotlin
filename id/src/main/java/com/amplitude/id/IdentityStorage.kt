package com.amplitude.id

interface IdentityStorage {

    fun setup(identityManager: IdentityManager)

    fun saveUserId(userId: String?)

    fun saveDeviceId(deviceId: String?)
}

interface IdentityStorageProvider {
    fun getIdentityStorage(configuration: IdConfiguration): IdentityStorage
}
