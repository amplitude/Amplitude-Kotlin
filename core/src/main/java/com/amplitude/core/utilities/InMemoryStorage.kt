package com.amplitude.core.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.diagnostics.DiagnosticsClientProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.ResponseHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

class InMemoryStorage
    @OptIn(RestrictedAmplitudeFeature::class)
    internal constructor(
        private val diagnosticsClientProvider: DiagnosticsClientProvider? = null,
    ) : Storage {
        constructor() : this(null)

        private val eventsBuffer: MutableList<BaseEvent> = mutableListOf()
        private val eventsListLock = Any()
        private val valuesMap = ConcurrentHashMap<String, String>()

        override suspend fun writeEvent(event: BaseEvent) {
            synchronized(eventsListLock) {
                eventsBuffer.add(event)
            }
        }

        override suspend fun write(
            key: Storage.Constants,
            value: String,
        ) {
            valuesMap[key.rawVal] = value
        }

        override suspend fun remove(key: Storage.Constants) {
            valuesMap.remove(key.rawVal)
        }

        override suspend fun rollover() {
        }

        override fun read(key: Storage.Constants): String? {
            return valuesMap[key.rawVal]
        }

        override fun readEventsContent(): List<Any> {
            val eventsToSend: List<BaseEvent>
            synchronized(eventsListLock) {
                eventsToSend = eventsBuffer.toList()
                eventsBuffer.clear()
            }
            // return List<List<BaseEvent>>
            return listOf(eventsToSend)
        }

        override suspend fun getEventsString(content: Any): String {
            // content is list of BaseEvent
            return JSONUtil.eventsToString(content as List<BaseEvent>)
        }

        @OptIn(RestrictedAmplitudeFeature::class)
        override fun getResponseHandler(
            eventPipeline: EventPipeline,
            configuration: Configuration,
            scope: CoroutineScope,
            storageDispatcher: CoroutineDispatcher,
        ): ResponseHandler {
            return InMemoryResponseHandler(
                eventPipeline,
                configuration,
                scope,
                storageDispatcher,
                diagnosticsClientProvider?.get(),
            )
        }

        fun removeEvents() {
            synchronized(eventsListLock) {
                eventsBuffer.clear()
            }
        }
    }

class InMemoryStorageProvider : StorageProvider {
    @OptIn(RestrictedAmplitudeFeature::class)
    override fun getStorage(
        amplitude: Amplitude,
        prefix: String?,
    ): Storage {
        return InMemoryStorage(DiagnosticsClientProvider { amplitude.diagnosticsClient })
    }
}
