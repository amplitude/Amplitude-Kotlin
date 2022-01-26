package com.amplitude.kotlin

import com.amplitude.kotlin.events.BaseEvent

interface Storage {
    fun write(event: BaseEvent)
}

interface StorageProvider {
    fun getStorage(amplitude: Amplitude): Storage
}