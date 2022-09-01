package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider

typealias EventCallBack = (BaseEvent, status: Int, message: String) -> Unit

open class Configuration @JvmOverloads constructor(
    val apiKey: String,
    open var flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    open var flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    open var instanceName: String = DEFAULT_INSTANCE,
    open var optOut: Boolean = false,
    open val storageProvider: StorageProvider = InMemoryStorageProvider(),
    open val loggerProvider: LoggerProvider = ConsoleLoggerProvider(),
    open var minIdLength: Int? = null,
    open var partnerId: String? = null,
    open var callback: EventCallBack? = null,
    open var flushMaxRetries: Int = FLUSH_MAX_RETRIES,
    open var useBatch: Boolean = false,
    open var serverZone: ServerZone = ServerZone.US,
    open var serverUrl: String? = null,
    open var plan: Plan? = null,
    open var ingestionMetadata: IngestionMetadata? = null
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
