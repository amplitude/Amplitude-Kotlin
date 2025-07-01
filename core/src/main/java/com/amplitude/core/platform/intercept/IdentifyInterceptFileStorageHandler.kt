package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.toEvents
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.FileNotFoundException

class IdentifyInterceptFileStorageHandler(
    private val storage: EventsFileStorage,
    private val logger: Logger,
    private val amplitude: Amplitude,
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
        var identifyEventUserProperties: MutableMap<String, Any>? = null
        for (eventPath in eventsData) {
            try {
                val eventsString = storage.getEventsString(eventPath)
                if (eventsString.isEmpty()) {
                    removeFile(eventPath as String)
                    continue
                }
                val eventsList = JSONArray(eventsString).toEvents()
                if (eventsList.isEmpty()) {
                    removeFile(eventPath as String)
                    continue
                }
                var events = eventsList
                if (event == null) {
                    event = eventsList[0]
                    identifyEventUserProperties = event.userProperties?.get(IdentifyOperation.SET.operationType) as? MutableMap<String, Any>
                    events = eventsList.subList(1, eventsList.size)
                }
                val userProperties = IdentifyInterceptorUtil.mergeIdentifyList(events)
                identifyEventUserProperties?.putAll(userProperties)
                removeFile(eventPath as String)
            } catch (e: Exception) {
                logger.warn("Identify Merge error: " + e.message)
                removeFile(eventPath as String)
            }
        }
        event?.userProperties?.put(
            IdentifyOperation.SET.operationType,
            identifyEventUserProperties ?: mutableMapOf<String, Any>(),
        )
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

    private fun removeFile(file: String) {
        amplitude.amplitudeScope.launch(amplitude.storageIODispatcher) {
            storage.removeFile(file)
        }
    }
}
