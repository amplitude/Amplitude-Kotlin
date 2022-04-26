package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider

typealias EventCallBack = (BaseEvent, status: Int, message: String) -> Unit

open class Configuration @JvmOverloads constructor(
    val apiKey: String,
    var flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    var flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    var instanceName: String = DEFAULT_INSTANCE,
    var optOut: Boolean = false,
    val storageProvider: StorageProvider = InMemoryStorageProvider(),
    val loggerProvider: LoggerProvider = ConsoleLoggerProvider(),
    var minIdLength: Int? = null,
    var partnerId: String? = null,
    val callback: EventCallBack? = null,
    val flushMaxRetries: Int = FLUSH_MAX_RETRIES,
    var useBatch: Boolean = false,
    var serverZone: ServerZone = ServerZone.US,
    var serverUrl: String? = null
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
         return minIdLength ?. let {
              it > 0
         } ?: let {
              true
         }
    }
}

enum class ServerZone {
    US, EU
}
