package com.amplitude.eventbridge

/**
 * Container of EventBridge
 */
public class EventBridgeContainer {
    public companion object {
        private val instancesLock = Any()
        private val instances = mutableMapOf<String, EventBridgeContainer>()

        @JvmStatic
        public fun getInstance(instanceName: String): EventBridgeContainer {
            return synchronized(instancesLock) {
                instances.getOrPut(instanceName) {
                    EventBridgeContainer()
                }
            }
        }
    }

    public val eventBridge: EventBridge = EventBridgeImpl()
}
