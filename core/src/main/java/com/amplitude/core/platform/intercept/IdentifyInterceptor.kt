package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.Constants
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.platform.plugins.AmplitudeDestination
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class IdentifyInterceptor(
    private val storage: Storage,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val logger: Logger,
    private val configuration: Configuration,
    private val plugin: AmplitudeDestination
) {

    private var transferScheduled = AtomicBoolean(false)

    private var userId: String? = null
    private var deviceId: String? = null
    private val identifySet = AtomicBoolean(false)


    private val storageHandler: IdentifyInterceptStorageHandler? = IdentifyInterceptStorageHandler.getIdentifyInterceptStorageHandler(storage, logger, scope, dispatcher)

    suspend fun intercept(event: BaseEvent): BaseEvent? {
        if (storageHandler == null) {
            // no-op to prevent custom storage errors
            return event
        }
        if (isIdentityUpdated(event)) {
            transferInterceptedIdentify()
        }
        when (event.eventType) {
            Constants.IDENTIFY_EVENT -> {
                return when {
                    isSetOnly(event) && !isSetGroups(event) -> {
                        // intercept and  save user properties
                        saveIdentifyProperties(event)
                        scheduleTransfer()
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

    private suspend fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent {
        return storageHandler!!.fetchAndMergeToNormalEvent(event)
    }

    private suspend fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent {
        return storageHandler!!.fetchAndMergeToIdentifyEvent(event)
    }

    private fun clearIdentifyIntercepts() {
        storageHandler!!.clearIdentifyIntercepts()
    }

    suspend fun transferInterceptedIdentify() {
        val event = getTransferIdentifyEvent()
        event?.let {
            plugin.enqueuePipeline(event)
        }
    }

    private suspend fun getTransferIdentifyEvent(): BaseEvent? {
        return storageHandler!!.getTransferIdentifyEvent()
    }

    private fun scheduleTransfer() = scope.launch(dispatcher) {
        while (!transferScheduled.get()) {
            transferScheduled.getAndSet(true)
            delay(configuration.identifyBatchIntervalMillis)
            transferInterceptedIdentify()
            transferScheduled.getAndSet(false)
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

    private fun isClearAll(event: BaseEvent): Boolean {
        return isActionOnly(event, IdentifyOperation.CLEAR_ALL)
    }

    private fun isSetOnly(event: BaseEvent): Boolean {
        return isActionOnly(event, IdentifyOperation.SET)
    }

    private fun isActionOnly(event: BaseEvent, action: IdentifyOperation): Boolean {
        event.userProperties?.let {
            return it.size == 1 && it.contains(action.operationType)
        }
        return false
    }

    private fun isSetGroups(event: BaseEvent) : Boolean {
        return event.groups != null && event.groups!!.isNotEmpty()
    }

    private fun isIdentityUpdated(event: BaseEvent): Boolean {
        if (!identifySet.getAndSet(true)) {
            userId = event.userId
            deviceId = event.deviceId
            return true
        }
        var isUpdated = false
        if (isIdUpdated(userId, event.userId)) {
            userId = event.userId
            isUpdated = true
        }
        if (isIdUpdated(deviceId, event.deviceId)) {
            deviceId = event.deviceId
            isUpdated = true
        }
        return isUpdated
    }

    private fun isIdUpdated(id: String?, updateId: String?): Boolean {
        if (id == null && updateId == null) {
            return false
        }
        return if (id == null || updateId == null) {
            true
        } else id != updateId
    }
}
