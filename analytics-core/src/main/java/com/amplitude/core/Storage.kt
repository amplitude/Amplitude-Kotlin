package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.ResponseHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

public interface Storage {
    public enum class Constants(public val rawVal: String) {
        LAST_EVENT_ID("last_event_id"),
        PREVIOUS_SESSION_ID("previous_session_id"),
        LAST_EVENT_TIME("last_event_time"),
        OPT_OUT("opt_out"),
        Events("events"),
        APP_VERSION("app_version"),
        APP_BUILD("app_build"),
        REMOTE_CONFIG("remote_config"),
        REMOTE_CONFIG_TIMESTAMP("remote_config_timestamp"),
    }

    public suspend fun writeEvent(event: BaseEvent)

    public suspend fun write(
        key: Constants,
        value: String,
    )

    public suspend fun remove(key: Constants)

    public suspend fun rollover()

    public fun read(key: Constants): String?

    public fun readEventsContent(): List<Any>

    public suspend fun getEventsString(content: Any): String

    public fun getResponseHandler(
        eventPipeline: EventPipeline,
        configuration: Configuration,
        scope: CoroutineScope,
        storageDispatcher: CoroutineDispatcher,
    ): ResponseHandler
}

public interface StorageProvider {
    public fun getStorage(
        amplitude: Amplitude,
        prefix: String? = null,
    ): Storage
}
