package com.amplitude.id

/**
 * Identify Container manages the identity like user id and device id,
 * shared by both Analytics and Experiment.
 *
 * @property configuration IdentityConfiguration for instance
 */
class IdentityContainer private constructor(val configuration: IdentityConfiguration) {
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

        @JvmStatic
        fun clearInstanceCache() {
            instances.clear()
        }
    }

    init {
        val identityStorage = configuration.identityStorageProvider.getIdentityStorage(configuration)
        identityManager = IdentityManagerImpl(identityStorage)
    }
}
