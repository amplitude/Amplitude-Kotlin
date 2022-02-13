package com.amplitude

interface IdentityStorage {

    fun setup(identityStore: IdentityStore)

    fun saveUserId(userId: String?)

    fun saveDeviceId(deviceId: String?)
}

interface IdentityStorageProvider {
    fun getIdentityStorage(): IdentityStorage
}
