package com.amplitude.kotlin

import com.amplitude.kotlin.events.BaseEvent

data class Configuration(
    val apiKey: String,
    val flushQueueSize: Int = Constants.FLUSH_QUEUE_SIZE,
    val flushIntervalMillis: Int = Constants.FLUSH_INTERVAL_MILLIS,
    val optOut: Boolean = false,
    val storageProvider: StorageProvider,
    val loggerProvider: LoggerProvider,
    val minIdLength: Int?,
    val callback: ((BaseEvent) -> Unit)?
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
