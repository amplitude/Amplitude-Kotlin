package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider

typealias EventCallBack = (BaseEvent, status: Int, message: String) -> Unit

open class Configuration(
    val apiKey: String,
    val flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    val flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    val instanceName: String = DEFAULT_INSTANCE,
    val optOut: Boolean = false,
    val storageProvider: StorageProvider = InMemoryStorageProvider(),
    val loggerProvider: LoggerProvider = ConsoleLoggerProvider(),
    val minIdLength: Int? = null,
    val partnerId: String? = null,
    val callback: EventCallBack? = null,
    val flushMaxRetries: Int = FLUSH_MAX_RETRIES,
    val useBatch: Boolean = false,
    val serverZone: ServerZone = ServerZone.US,
    val serverUrl: String? = null
) {

    companion object {
        const val FLUSH_QUEUE_SIZE = 30
        const val FLUSH_INTERVAL_MILLIS = 30 * 1000 // 30s
        const val FLUSH_MAX_RETRIES = 5
        const val DEFAULT_INSTANCE = "\$default_instance"
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

enum class ServerZone {
    US, EU
}
