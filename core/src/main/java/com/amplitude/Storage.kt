package com.amplitude

import com.amplitude.events.BaseEvent

interface Storage {
    fun write(event: BaseEvent)

    fun rollover()

    fun getEvents(): List<String>
}

interface StorageProvider {
    fun getStorage(amplitude: Amplitude): Storage
}
