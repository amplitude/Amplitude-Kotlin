package com.amplitude.core

import com.amplitude.core.events.BaseEvent

interface Storage {

    enum class Constants(val rawVal: String) {
        LAST_EVENT_ID("last_event_id"),
        PREVIOUS_SESSION_ID("previous_session_id"),
        LAST_EVENT_TIME("last_event_time"),
        OPT_OUT("opt_out"),
        Events("events")
    }

    suspend fun writeEvent(event: BaseEvent)

    suspend fun write(key: Constants, value: String)

    suspend fun rollover()

    fun read(key: Constants): String?

    fun readEventsContent(): List<Any>

    fun getEventsString(content: Any) : String
}

interface StorageProvider {
    fun getStorage(amplitude: Amplitude): Storage
}
