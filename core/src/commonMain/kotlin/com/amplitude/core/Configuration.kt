package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityStorageProvider

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
    open var ingestionMetadata: IngestionMetadata? = null,
    open var identifyBatchIntervalMillis: Long = IDENTIFY_BATCH_INTERVAL_MILLIS,
    open var identifyInterceptStorageProvider: StorageProvider = InMemoryStorageProvider(),
    open var identityStorageProvider: IdentityStorageProvider = IMIdentityStorageProvider(),
    open var offline: Boolean? = false,
    open var deviceId: String? = null,
    open var sessionId: Long? = null,
    open var httpClient: HttpClientInterface? = null,
) {

    companion object {
        const val FLUSH_QUEUE_SIZE = 30
        const val FLUSH_INTERVAL_MILLIS = 30 * 1000 // 30s
        const val FLUSH_MAX_RETRIES = 5
        const val DEFAULT_INSTANCE = "\$default_instance"
        const val IDENTIFY_BATCH_INTERVAL_MILLIS = 30 * 1000L // 30s
    }

    fun isValid(): Boolean {
        return apiKey.isNotBlank() && flushQueueSize > 0 && flushIntervalMillis > 0 && isMinIdLengthValid()
    }

    private fun isMinIdLengthValid(): Boolean {
        return minIdLength?.let {
            it > 0
        } ?: let {
            true
        }
    }

    fun getApiHost(): String {
        return this.serverUrl ?: with(this) {
            when {
                serverZone == ServerZone.EU && useBatch -> Constants.EU_BATCH_API_HOST
                serverZone == ServerZone.EU -> Constants.EU_DEFAULT_API_HOST
                useBatch -> Constants.BATCH_API_HOST
                else -> Constants.DEFAULT_API_HOST
            }
        }
    }
}

enum class ServerZone {
    US, EU
}
