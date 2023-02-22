package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.FileStorage
import com.amplitude.core.utilities.toEvents
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException

class IdentifyInterceptFileStorageHandler(
    private val storage: FileStorage,
    private val logger: Logger,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher
) : IdentifyInterceptStorageHandler {
    override suspend fun getTransferIdentifyEvent(): BaseEvent? {
        val eventsData = storage.readEventsContent()
        if (eventsData.isEmpty()) {
            return null
        }
        var event: BaseEvent? = null
        var identifyEventUserProperties: MutableMap<String, Any?>? = null
        for (eventPath in eventsData) {
            try {
                val eventsString = storage.getEventsString(eventPath)
                if (eventsString.isEmpty()) continue
                val eventsList = JSONArray(eventsString).toEvents()
                var events = eventsList
                if (event == null) {
                    event = eventsList[0]
                    identifyEventUserProperties = event.userProperties!![IdentifyOperation.SET.operationType] as MutableMap<String, Any?>
                    events = eventsList.subList(0, eventsList.size)
                }
                val userProperties = IdentifyInterceptorUtil.mergeIdentifyList(events)
                identifyEventUserProperties?.putAll(userProperties)
                removeFile(eventPath as String)
            } catch (e: JSONException) {
                logger.warn("Identify Merge error: " + e.message)
                removeFile(eventPath as String)
            }
            event?.userProperties!![IdentifyOperation.SET.operationType] =
                identifyEventUserProperties
        }
        return event
    }

    override suspend fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent {
        val eventsData = storage.readEventsContent()
        if (eventsData.isEmpty()) {
            return event
        }
        val userProperties = mutableMapOf<String, Any?>()
        for (eventPath in eventsData) {
            try {
                val eventsString = storage.getEventsString(eventPath)
                if (eventsString.isEmpty()) {
                    continue
                }
                val events = JSONArray(eventsString).toEvents()
                val listUserProperties = IdentifyInterceptorUtil.mergeIdentifyList(events)
                userProperties.putAll(listUserProperties)
                removeFile(eventPath as String)
            } catch (e: JSONException) {
                logger.warn("Identify Merge error: " + e.message)
                removeFile(eventPath as String)
            }
            event.userProperties?.let {
                userProperties.putAll(it)
            }
            event.userProperties = userProperties
        }
        return event
    }

    override suspend fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent {
        val eventsData = storage.readEventsContent()
        if (eventsData.isEmpty()) {
            return event
        }
        val userProperties = mutableMapOf<String, Any?>()
        for (eventPath in eventsData) {
            try {
                val eventsString = storage.getEventsString(eventPath)
                if (eventsString.isEmpty()) continue
                val events = JSONArray(eventsString).toEvents()
                val listUserProperties = IdentifyInterceptorUtil.mergeIdentifyList(events)
                userProperties.putAll(listUserProperties)
                removeFile(eventPath as String)
            } catch (e: JSONException) {
                logger.warn("Identify Merge error: " + e.message)
                removeFile(eventPath as String)
            }
            if (event.userProperties?.contains(IdentifyOperation.SET.operationType) == true) {
                userProperties.putAll(event.userProperties!![IdentifyOperation.SET.operationType] as MutableMap<String, Any?>)
            }
            event.userProperties?.put(IdentifyOperation.SET.operationType, userProperties)
        }
        return event
    }

    override fun clearIdentifyIntercepts() {
        val eventsData = storage.readEventsContent()
        if (eventsData.isEmpty()) {
            return
        }
        for (eventPath in eventsData) {
            removeFile(eventPath as String)
        }
    }

    private fun removeFile(file: String) {
        scope.launch(dispatcher) {
            storage.removeFile(file)
        }
    }
}
