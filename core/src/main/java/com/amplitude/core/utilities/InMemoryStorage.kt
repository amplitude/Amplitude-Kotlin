package com.amplitude.core.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent

class InMemoryStorage(
    val amplitude: Amplitude
) : Storage {

    val eventsBuffer: MutableList<BaseEvent> = mutableListOf()
    val eventsListLock = Any()

    override fun write(event: BaseEvent) {
        synchronized(eventsListLock) {
            eventsBuffer.add(event)
        }
    }

    override fun rollover() {

    }

    override fun getEvents(): List<String> {
        val eventsToSend: List<BaseEvent>
        synchronized(eventsListLock) {
            eventsToSend = ArrayList(eventsBuffer)
            eventsBuffer.clear()
        }
        return JSONUtil.eventsToString(eventsToSend)
    }
}

class InMemoryStorageProvider : StorageProvider {
    override fun getStorage(amplitude: Amplitude): Storage {
        return InMemoryStorage(amplitude)
    }
}