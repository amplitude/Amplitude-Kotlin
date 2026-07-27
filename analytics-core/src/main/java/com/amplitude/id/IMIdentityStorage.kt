package com.amplitude.id

/**
 * In Memory Identity Storage
 */
public class IMIdentityStorage : IdentityStorage {
    public var userId: String? = null
    public var deviceId: String? = null

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
public class IMIdentityStorageProvider : IdentityStorageProvider {
    override fun getIdentityStorage(configuration: IdentityConfiguration): IdentityStorage {
        return IMIdentityStorage()
    }
}
