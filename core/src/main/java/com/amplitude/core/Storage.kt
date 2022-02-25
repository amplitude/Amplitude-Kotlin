package com.amplitude.core

import com.amplitude.core.events.BaseEvent

interface Storage {
    fun write(event: BaseEvent)

    fun rollover()

    fun getEvents(): List<String>
}

interface StorageProvider {
    fun getStorage(amplitude: Amplitude): Storage
}
