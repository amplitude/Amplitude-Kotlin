package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyOperation
import com.amplitude.core.utilities.FileStorage
import com.amplitude.core.utilities.InMemoryStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

interface IdentifyInterceptStorageHandler {
    suspend fun getTransferIdentifyEvent(): BaseEvent?

    suspend fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent

    suspend fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent

    fun clearIdentifyIntercepts()

    companion object {
        fun getIdentifyInterceptStorageHandler(storage: Storage, logger: Logger, scope: CoroutineScope, dispatcher: CoroutineDispatcher): IdentifyInterceptStorageHandler? {
            return when (storage) {
                is FileStorage -> {
                    IdentifyInterceptFileStorageHandler(storage, logger, scope, dispatcher)
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
