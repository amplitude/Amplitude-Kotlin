package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.toEvents
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import java.io.FileNotFoundException

class IdentifyInterceptFileStorageHandler(
    private val storage: EventsFileStorage,
    private val logger: Logger,
    private val amplitude: Amplitude
) : IdentifyInterceptStorageHandler {
    override suspend fun getTransferIdentifyEvent(): BaseEvent? {
        try {
            storage.rollover()
        } catch (e: FileNotFoundException) {
            e.message?.let {
                logger.warn("Event storage file not found: $it")
            }
            return null
        }
        val eventsData = storage.readEventsContent()
        if (eventsData.isEmpty()) {
            return null
        }
        var event: BaseEvent? = null
        var identifyEventUserProperties: MutableMap<String, Any?>? = null
        for (eventPath in eventsData) {
            try {
                val eventsString = storage.getEventsString(eventPath)
                if (eventsString.isEmpty()) {
                    removeFile(eventPath as String)
                    continue
                }
                val eventsList = JSONArray(eventsString).toEvents()
                var events = eventsList
                if (event == null) {
                    event = eventsList[0]
                    identifyEventUserProperties = event?.userProperties?.get(IdentifyOperation.SET.operationType) as MutableMap<String, Any?>
                    events = eventsList.subList(1, eventsList.size)
                }
                val userProperties = IdentifyInterceptorUtil.mergeIdentifyList(events)
                identifyEventUserProperties?.putAll(userProperties)
                removeFile(eventPath as String)
            } catch (e: JSONException) {
                logger.warn("Identify Merge error: " + e.message)
                removeFile(eventPath as String)
            }
        }
        event?.userProperties?.put(
            IdentifyOperation.SET.operationType,
            identifyEventUserProperties
        )
        return event
    }

    override suspend fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent {
        val userProperties = fetchAndMergeToUserProperties()
        event.userProperties?.let {
            userProperties.putAll(it)
        }
        event.userProperties = userProperties
        return event
    }

    override suspend fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent {
        val userProperties = fetchAndMergeToUserProperties()
        if (event.userProperties?.contains(IdentifyOperation.SET.operationType) == true) {
            userProperties.putAll(event.userProperties!![IdentifyOperation.SET.operationType] as MutableMap<String, Any?>)
        }
        event.userProperties?.put(IdentifyOperation.SET.operationType, userProperties)
        return event
    }

    override suspend fun clearIdentifyIntercepts() {
        try {
            storage.rollover()
        } catch (e: FileNotFoundException) {
            e.message?.let {
                logger.warn("Event storage file not found: $it")
            }
            return
        }
        val eventsData = storage.readEventsContent()
        if (eventsData.isEmpty()) {
            return
        }
        for (eventPath in eventsData) {
            removeFile(eventPath as String)
        }
    }

    private suspend fun fetchAndMergeToUserProperties(): MutableMap<String, Any?> {
        val userProperties = mutableMapOf<String, Any?>()
        try {
            storage.rollover()
        } catch (e: FileNotFoundException) {
            e.message?.let {
                logger.warn("Event storage file not found: $it")
            }
            return userProperties
        }
        val eventsData = storage.readEventsContent()
        if (eventsData.isEmpty()) {
            return userProperties
        }

        for (eventPath in eventsData) {
            try {
                val eventsString = storage.getEventsString(eventPath)
                if (eventsString.isEmpty()) {
                    removeFile(eventPath as String)
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
        }
        return userProperties
    }

    private fun removeFile(file: String) {
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            storage.removeFile(file)
        }
    }
}
