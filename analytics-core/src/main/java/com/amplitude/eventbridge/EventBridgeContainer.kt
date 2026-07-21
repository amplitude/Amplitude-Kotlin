package com.amplitude.eventbridge

/**
 * Container of EventBridge
 */
class EventBridgeContainer {
    companion object {
        private val instancesLock = Any()
        private val instances = mutableMapOf<String, EventBridgeContainer>()

        @JvmStatic
        fun getInstance(instanceName: String): EventBridgeContainer {
            return synchronized(instancesLock) {
                instances.getOrPut(instanceName) {
                    EventBridgeContainer()
                }
            }
        }

        /** Removes the container for [instanceName], but only if it is still [expected]. */
        internal fun remove(
            instanceName: String,
            expected: EventBridgeContainer,
        ) {
            synchronized(instancesLock) {
                if (instances[instanceName] === expected) {
                    instances.remove(instanceName)
                }
            }
        }
    }

    val eventBridge: EventBridge = EventBridgeImpl()
}
