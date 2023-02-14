package com.amplitude.core.platform.intercept

import com.amplitude.common.Logger
import com.amplitude.core.Storage
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.FileStorage
import com.amplitude.core.utilities.InMemoryStorage

interface IdentifyInterceptStorageHandler {
    fun getTransferIdentifyEvent(): BaseEvent?

    fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent

    fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent

    companion object {
        fun getIdentifyInterceptStorageHandler(storage: Storage, logger: Logger): IdentifyInterceptStorageHandler? {
            return when (storage) {
                is FileStorage -> {
                    IdentifyInterfectFileStorageHandler(storage)
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
