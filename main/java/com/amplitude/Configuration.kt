package com.amplitude

import com.amplitude.events.BaseEvent
import com.amplitude.utilities.ConsoleLoggerProvider
import com.amplitude.utilities.InMemoryStorageProvider

open class Configuration(
    val apiKey: String,
    val flushQueueSize: Int = Constants.FLUSH_QUEUE_SIZE,
    val flushIntervalMillis: Int = Constants.FLUSH_INTERVAL_MILLIS,
    val optOut: Boolean = false,
    val storageProvider: StorageProvider = InMemoryStorageProvider(),
    val loggerProvider: LoggerProvider = ConsoleLoggerProvider(),
    val minIdLength: Int? = null,
    val callback: ((BaseEvent) -> Unit)? = null
) {
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
