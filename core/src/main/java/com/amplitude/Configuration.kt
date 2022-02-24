package com.amplitude

import com.amplitude.events.BaseEvent
import com.amplitude.utilities.ConsoleLoggerProvider
import com.amplitude.utilities.InMemoryStorageProvider

open class Configuration(
    val apiKey: String,
    val flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    val flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    val optOut: Boolean = false,
    val storageProvider: StorageProvider = InMemoryStorageProvider(),
    val loggerProvider: LoggerProvider = ConsoleLoggerProvider(),
    val minIdLength: Int? = null,
    val callback: ((BaseEvent) -> Unit)? = null
) {

    companion object {
        const val FLUSH_QUEUE_SIZE = 30
        const val FLUSH_INTERVAL_MILLIS = 30 * 1000 // 30s
    }

    fun isValid(): Boolean {
        return apiKey.isNotBlank() && flushQueueSize > 0 && flushIntervalMillis > 0 && isMinIdLengthValid()
    }

    fun isMinIdLengthValid(): Boolean {
        if (minIdLength == null) {
            return true
        }
        return minIdLength > 0
    }
}
