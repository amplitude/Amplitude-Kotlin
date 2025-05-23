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
    }

    val eventBridge: EventBridge = EventBridgeImpl()
}
