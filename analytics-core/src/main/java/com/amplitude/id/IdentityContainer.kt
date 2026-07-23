package com.amplitude.id

/**
 * Identify Container manages the identity like user id and device id,
 * shared by both Analytics and Experiment.
 *
 * @property configuration IdentityConfiguration for instance
 */
public class IdentityContainer private constructor(public val configuration: IdentityConfiguration) {
    public val identityManager: IdentityManager

    public companion object {
        private val instancesLock = Any()
        private val instances = mutableMapOf<String, IdentityContainer>()

        @JvmStatic
        public fun getInstance(configuration: IdentityConfiguration): IdentityContainer {
            return synchronized(instancesLock) {
                instances.getOrPut(configuration.instanceName) {
                    IdentityContainer(configuration)
                }
            }
        }

        @JvmStatic
        public fun clearInstanceCache() {
            instances.clear()
        }
    }

    init {
        val identityStorage = configuration.identityStorageProvider.getIdentityStorage(configuration)
        identityManager = IdentityManagerImpl(identityStorage)
    }
}
