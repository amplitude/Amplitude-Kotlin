package com.amplitude.id

class IdContainer private constructor(val identityStorageProvider: IdentityStorageProvider) {
    var identityStorage: IdentityStorage
    var identityStore: IdentityStore

    companion object {

        private val instancesLock = Any()
        private val instances = mutableMapOf<String, IdContainer>()

        @JvmStatic
        fun getInstance(apiKey: String, identityStorageProvider: IdentityStorageProvider): IdContainer {
            return synchronized(instancesLock) {
                instances.getOrPut(apiKey) {
                    IdContainer(identityStorageProvider)
                }
            }
        }
    }

    init {
        identityStore = IdentityStoreImpl()
        identityStorage = identityStorageProvider.getIdentityStorage()
        identityStorage.setup(identityStore)
    }
}