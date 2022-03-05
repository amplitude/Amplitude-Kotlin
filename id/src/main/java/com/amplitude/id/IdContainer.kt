package com.amplitude.id

class IdContainer private constructor(val configuration: IdConfiguration) {
    private val identityStorage: IdentityStorage
    val identityManager: IdentityManager

    companion object {

        private val instancesLock = Any()
        private val instances = mutableMapOf<String, IdContainer>()

        @JvmStatic
        fun getInstance(configuration: IdConfiguration): IdContainer {
            return synchronized(instancesLock) {
                instances.getOrPut(configuration.instanceName) {
                    IdContainer(configuration)
                }
            }
        }
    }

    init {
        identityManager = IdentityManagerImpl()
        identityStorage = configuration.identityStorageProvider.getIdentityStorage(configuration)
        identityStorage.setup(identityManager)
    }
}