package com.amplitude.core.platform.intercept

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.InMemoryStorage

class IdentifyInterceptInMemoryStorageHandler(
    private val storage: InMemoryStorage
) : IdentifyInterceptStorageHandler {
    override fun getTransferIdentifyEvent(): BaseEvent? {
        val eventsData = storage.readEventsContent() as List<List<BaseEvent>>
        if (eventsData.isEmpty() || eventsData[0].isEmpty()) {
            return null
        }
        val events = eventsData[0]
        val identifyEvent = events[0]
        val identifyEventUserProperties = identifyEvent.userProperties!!.get(IdentifyOperation.SET.operationType) as MutableMap<String, Any?>
        val userProperties = mutableMapOf<String, Any?>()
        events.subList(1, events.size).forEach {
            userProperties.putAll(it.userProperties!!.get(IdentifyOperation.SET.operationType) as MutableMap<String, Any?>)
        }
        identifyEventUserProperties.putAll(userProperties)
        identifyEvent.userProperties = identifyEventUserProperties
        return identifyEvent
    }

    override fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent {
        val eventsData = storage.readEventsContent() as List<List<BaseEvent>>
        if (eventsData.isEmpty() || eventsData[0].isEmpty()) {
            return event
        }
        val events = eventsData[0]
        val userProperties = mutableMapOf<String, Any?>()
        events.forEach {
            userProperties.putAll(it.userProperties!!.get(IdentifyOperation.SET.operationType) as MutableMap<String, Any?>)
        }
        event.userProperties?.let {
            userProperties.putAll(it)
        }
        event.userProperties = userProperties
        return event
    }

    override fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent {
        TODO("Not yet implemented")
    }
}
