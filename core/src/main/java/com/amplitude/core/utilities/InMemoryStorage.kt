package com.amplitude.core.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import java.util.concurrent.ConcurrentHashMap

class InMemoryStorage(
    val amplitude: Amplitude
) : Storage {

    val eventsBuffer: MutableList<BaseEvent> = mutableListOf()
    val eventsListLock = Any()
    val valuesMap = ConcurrentHashMap<String, String>()

    override suspend fun writeEvent(event: BaseEvent) {
        synchronized(eventsListLock) {
            eventsBuffer.add(event)
        }
    }

    override suspend fun write(key: Storage.Constants, value: String) {
        valuesMap.put(key.rawVal, value)
    }

    override suspend fun rollover() {
    }

    override fun read(key: Storage.Constants): String? {
        return valuesMap.getOrDefault(key.rawVal, null)
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
