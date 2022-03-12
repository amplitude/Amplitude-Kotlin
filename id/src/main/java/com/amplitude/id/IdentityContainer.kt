package com.amplitude.id

class IdentityContainer private constructor(val configuration: IdentityConfiguration) {
    private val identityStorage: IdentityStorage
    val identityManager: IdentityManager

    companion object {

        private val instancesLock = Any()
        private val instances = mutableMapOf<String, IdentityContainer>()

        @JvmStatic
        fun getInstance(configuration: IdentityConfiguration): IdentityContainer {
            return synchronized(instancesLock) {
                instances.getOrPut(configuration.instanceName) {
                    IdentityContainer(configuration)
                }
            }
        }
    }

    init {
        identityStorage = configuration.identityStorageProvider.getIdentityStorage(configuration)
        identityManager = IdentityManagerImpl(identityStorage)
    }
}
