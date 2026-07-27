package com.amplitude.id

public interface IdentityStorage {
    public fun load(): Identity

    public fun saveUserId(userId: String?)

    public fun saveDeviceId(deviceId: String?)

    public fun delete()
}

public interface IdentityStorageProvider {
    public fun getIdentityStorage(configuration: IdentityConfiguration): IdentityStorage
}
