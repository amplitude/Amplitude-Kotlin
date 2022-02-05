package com.amplitude

import com.amplitude.events.BaseEvent

interface Storage {
    fun write(event: BaseEvent)
}

interface StorageProvider {
    fun getStorage(amplitude: com.amplitude.Amplitude): Storage
}
