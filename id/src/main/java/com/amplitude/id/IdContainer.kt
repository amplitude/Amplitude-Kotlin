package com.amplitude.id

class IdContainer private constructor(val apiKey: String, private val identityStorageProvider: IdentityStorageProvider) {
    private val identityStorage: IdentityStorage
    val identityStore: IdentityStore

    companion object {

        private val instancesLock = Any()
        private val instances = mutableMapOf<String, IdContainer>()

        @JvmStatic
        fun getInstance(apiKey: String, identityStorageProvider: IdentityStorageProvider): IdContainer {
            return synchronized(instancesLock) {
                instances.getOrPut(apiKey) {
                    IdContainer(apiKey, identityStorageProvider)
                }
            }
        }
    }

    init {
        identityStore = IdentityStoreImpl()
        identityStorage = identityStorageProvider.getIdentityStorage(apiKey)
        identityStorage.setup(identityStore)
    }
}