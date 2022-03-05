package com.amplitude.id

class IMIdentityStorage: IdentityStorage {
    lateinit var identityStore: IdentityManager
    var userId: String? = null
    var deviceId: String? = null

    override fun setup(identityManager: IdentityManager) {
        this.identityStore = identityManager
        identityManager.addIdentityListener(IMIdentityListener(this))
        load()
    }

    private fun load() {
        identityStore.setIdentity(Identity(userId, deviceId), IdentityUpdateType.Initialized)
    }

    override fun saveUserId(userId: String?) {
        this.userId = userId
    }

    override fun saveDeviceId(deviceId: String?) {
        this.deviceId = deviceId
    }
}

class IMIdentityStorageProvider: IdentityStorageProvider {
    override fun getIdentityStorage(configuration: IdConfiguration): IdentityStorage {
        return IMIdentityStorage()
    }
}

class IMIdentityListener(private val identityStorage: IMIdentityStorage) : IdentityListener {

    override fun onUserIdChange(userId: String?) {
        identityStorage.saveUserId(userId)
    }

    override fun onDeviceIdChange(deviceId: String?) {
        identityStorage.saveDeviceId(deviceId)
    }

    override fun onIdentityChanged(identity: Identity, updateType: IdentityUpdateType) {

    }
}
