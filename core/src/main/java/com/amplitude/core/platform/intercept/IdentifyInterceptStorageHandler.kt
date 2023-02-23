package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.InMemoryStorage

interface IdentifyInterceptStorageHandler {
    suspend fun getTransferIdentifyEvent(): BaseEvent?

    suspend fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent

    suspend fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent

    suspend fun clearIdentifyIntercepts()

    companion object {
        fun getIdentifyInterceptStorageHandler(storage: Storage, logger: Logger, amplitude: Amplitude): IdentifyInterceptStorageHandler? {
            return when (storage) {
                is EventsFileStorage -> {
                    IdentifyInterceptFileStorageHandler(storage, logger, amplitude)
                }
                is InMemoryStorage -> {
                    IdentifyInterceptInMemoryStorageHandler(storage)
                }
                else -> {
                    logger.warn("Custom storage, identify intercept not started")
                    null
                }
            }
        }
    }
}

object IdentifyInterceptorUtil {
    fun mergeIdentifyList(events: List<BaseEvent>): MutableMap<String, Any?> {
        val userProperties = mutableMapOf<String, Any?>()
        events.forEach {
            userProperties.putAll(it.userProperties!!.get(IdentifyOperation.SET.operationType) as MutableMap<String, Any?>)
        }
        return userProperties
    }
}
