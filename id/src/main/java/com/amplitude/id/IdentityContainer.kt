package com.amplitude.id

class IdentityContainer private constructor(val configuration: IdConfiguration) {
    private val identityStorage: IdentityStorage
    val identityManager: IdentityManager

    companion object {

        private val instancesLock = Any()
        private val instances = mutableMapOf<String, IdentityContainer>()

        @JvmStatic
        fun getInstance(configuration: IdConfiguration): IdentityContainer {
            return synchronized(instancesLock) {
                instances.getOrPut(configuration.instanceName) {
                    IdentityContainer(configuration)
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