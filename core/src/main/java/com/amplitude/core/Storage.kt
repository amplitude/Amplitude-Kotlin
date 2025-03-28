package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.ResponseHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

interface Storage {
    enum class Constants(val rawVal: String) {
        LAST_EVENT_ID("last_event_id"),
        PREVIOUS_SESSION_ID("previous_session_id"),
        LAST_EVENT_TIME("last_event_time"),
        OPT_OUT("opt_out"),
        Events("events"),
        APP_VERSION("app_version"),
        APP_BUILD("app_build"),
    }

    suspend fun writeEvent(event: BaseEvent)

    suspend fun write(
        key: Constants,
        value: String,
    )

    suspend fun remove(key: Constants)

    suspend fun rollover()

    fun read(key: Constants): String?

    fun readEventsContent(): List<Any>

    suspend fun getEventsString(content: Any): String

    fun getResponseHandler(
        eventPipeline: EventPipeline,
        configuration: Configuration,
        scope: CoroutineScope,
        storageDispatcher: CoroutineDispatcher,
    ): ResponseHandler
}

interface StorageProvider {
    fun getStorage(
        amplitude: Amplitude,
        prefix: String? = null,
    ): Storage
}
