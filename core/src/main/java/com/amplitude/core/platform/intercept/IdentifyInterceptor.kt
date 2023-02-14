package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.Constants
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.utilities.toBaseEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class IdentifyInterceptor (
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val configuration: Configuration,
    private val plugin: AmplitudeDestination
    ) {

    private var transferScheduled = false

    private val storageHandler: IdentifyInterceptStorageHandler? = IdentifyInterceptStorageHandler.getIdentifyInterceptStorageHandler(storage)

    fun intercept(event: BaseEvent) : BaseEvent? {
        if (storageHandler == null) {
            // no-op to prevent custom storage errors
            return event
        }
        when (event.eventType) {
            Constants.IDENTIFY_EVENT -> {
                return when {
                    isSetOnly(event) -> {
                        // intercept and  save user properties
                        saveIdentifyProperties(event)
                        transferInterceptedIdentify()
                        null
                    }
                    isClearAll(event) -> {
                        // clear existing and return event
                        clearIdentifyIntercepts()
                        event
                    }
                    else -> {
                        // Fetch and merge event
                        fetchAndMergeToIdentifyEvent(event)
                    }
                }
            }
            Constants.GROUP_IDENTIFY_EVENT -> {
                // no op
                return event
            }
            else -> {
                // fetch, merge and attach user properties
                return fetchAndMergeToNormalEvent(event)
            }
        }
    }

    private fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent {
        return storageHandler!!.fetchAndMergeToNormalEvent()
    }

    private fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent {
        return storageHandler!!.fetchAndMergeToIdentifyEvent()
    }

    private fun clearIdentifyIntercepts() {
        TODO("Not yet implemented")
    }

    fun transferInterceptedIdentify() {
        val event = storageHandler!!.getTransferIdentifyEvent()
        event?.let {
            plugin.enqueuePipeline(event)
        }
    }

    suspend fun getTransferIdentifyEvent() : BaseEvent? {
        val eventsData = storage.readEventsContent()
        var identifyEvent: JSONObject? = null
        for (events in eventsData) {
            try {
                val eventsString = storage.getEventsString(events)
                if (eventsString.isEmpty()) continue
                val eventsJSONArray = JSONArray(eventsString)
                val identifys = eventsJSONArray.toList()
                if (identifyEvent == null) {
                    identifyEvent = identifys[0] as JSONObject
                }
                val identifyEventUserProperties = identifyEvent.getJSONObject("user_properties")
                    .getJSONObject(IdentifyOperation.SET.operationType)
                val userProperties =
                    mergeIdentifyInterceptList(identifys.subList(1, identifys.size))
                mergeUserProperties(identifyEventUserProperties, userProperties)
                identifyEvent.getJSONObject("user_properties")
                    .put(IdentifyOperation.SET.operationType, identifyEventUserProperties)
            } catch (e: JSONException) {
                logger.warn("Identify Merge error: " + e.message);
            }
        }
        return identifyEvent?.toBaseEvent()
    }

    private fun scheduleTransfer() = scope.launch(dispatcher) {
        while (!transferScheduled) {
            transferScheduled = true
            delay(configuration.identifyBatchIntervalMillis)
            transferInterceptedIdentify()
            transferScheduled = false
        }
    }

    private fun saveIdentifyProperties(event: BaseEvent) = scope.launch(dispatcher) {
        try {
            storage.writeEvent(event)
        } catch (e: Exception) {
            e.message?.let {
                logger.error("Error when write event: $it")
            }
        }
    }

    @Throws(JSONException::class)
    fun mergeIdentifyInterceptList(identifys: MutableList<Any>): JSONObject {
        val userProperties = JSONObject()
        for (identify in identifys) {
            val setUserProperties = (identify as JSONObject).getJSONObject("user_properties")
                .getJSONObject(IdentifyOperation.SET.operationType)
            mergeUserProperties(userProperties, setUserProperties)
        }
        return userProperties
    }

    @Throws(JSONException::class)
    fun mergeUserProperties(
        userProperties: JSONObject,
        userPropertiesToMerge: JSONObject
    ) {
        val keys: Iterator<*> = userPropertiesToMerge.keys()
        while (keys.hasNext()) {
            val key = keys.next() as String
            userProperties.put(key, userPropertiesToMerge[key])
        }
    }

    private fun isClearAll(event: BaseEvent): Boolean {
        return isActionOnly(event, IdentifyOperation.CLEAR_ALL)
    }

    private fun isSetOnly(event: BaseEvent): Boolean {
        return isActionOnly(event, IdentifyOperation.SET)
    }

    private fun isActionOnly(event: BaseEvent, action: IdentifyOperation): Boolean {
        event.userProperties?.let{
            return it.size == 1 && it.contains(action.operationType)
        }
        return false
    }
}

