package com.amplitude.id

/**
 * In Memory Identity Storage
 */
class IMIdentityStorage : IdentityStorage {
    var userId: String? = null
    var deviceId: String? = null

    override fun load(): Identity {
        return Identity(userId, deviceId)
    }

    override fun saveUserId(userId: String?) {
        this.userId = userId
    }

    override fun saveDeviceId(deviceId: String?) {
        this.deviceId = deviceId
    }

    override fun delete() {
        userId = null
        deviceId = null
    }
}

/**
 * In Memory Identity Storage Provider
 */
class IMIdentityStorageProvider : IdentityStorageProvider {
    override fun getIdentityStorage(configuration: IdentityConfiguration): IdentityStorage {
        return IMIdentityStorage()
    }
}
