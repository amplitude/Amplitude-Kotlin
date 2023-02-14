package com.amplitude.core.platform.intercept

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.FileStorage

class IdentifyInterfectFileStorageHandler(
    private val storage: FileStorage
) : IdentifyInterceptStorageHandler {
    override fun getTransferIdentifyEvent(): BaseEvent? {
        TODO("Not yet implemented")
    }

    override fun fetchAndMergeToNormalEvent(event: BaseEvent): BaseEvent {
        TODO("Not yet implemented")
    }

    override fun fetchAndMergeToIdentifyEvent(event: BaseEvent): BaseEvent {
        TODO("Not yet implemented")
    }
}