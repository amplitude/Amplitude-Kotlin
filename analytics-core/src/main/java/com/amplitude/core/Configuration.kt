package com.amplitude.core

import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityStorageProvider

public typealias EventCallBack = (BaseEvent, status: Int, message: String) -> Unit

/**
 * When adding new constructor parameters, also update [ConfigurationBuilder.build] to pass the
 * new value through.
 */
public open class Configuration
    @JvmOverloads
    constructor(
        public val apiKey: String,
        public open var flushQueueSize: Int = FLUSH_QUEUE_SIZE,
        public open var flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
        public open var instanceName: String = DEFAULT_INSTANCE,
        public open var optOut: Boolean = false,
        public open val storageProvider: StorageProvider = InMemoryStorageProvider(),
        public open val loggerProvider: LoggerProvider = ConsoleLoggerProvider(),
        public open var minIdLength: Int? = null,
        public open var partnerId: String? = null,
        public open var callback: EventCallBack? = null,
        public open var flushMaxRetries: Int = FLUSH_MAX_RETRIES,
        public open var useBatch: Boolean = false,
        public open var serverZone: ServerZone = ServerZone.US,
        public open var serverUrl: String? = null,
        public open var plan: Plan? = null,
        public open var ingestionMetadata: IngestionMetadata? = null,
        public open var identifyBatchIntervalMillis: Long = IDENTIFY_BATCH_INTERVAL_MILLIS,
        public open var identifyInterceptStorageProvider: StorageProvider = InMemoryStorageProvider(),
        public open var identityStorageProvider: IdentityStorageProvider = IMIdentityStorageProvider(),
        public open var offline: Boolean? = false,
        public open var deviceId: String? = null,
        public open var sessionId: Long? = null,
        public open var httpClient: HttpClientInterface? = null,
        public open var enableDiagnostics: Boolean = true,
        public open var enableRequestBodyCompression: Boolean = false,
    ) {
        public companion object {
            public const val FLUSH_QUEUE_SIZE: Int = 30
            public const val FLUSH_INTERVAL_MILLIS: Int = 30 * 1000 // 30s
            public const val FLUSH_MAX_RETRIES: Int = 5
            public const val DEFAULT_INSTANCE: String = "\$default_instance"
            public const val IDENTIFY_BATCH_INTERVAL_MILLIS: Long = 30 * 1000L // 30s
        }

        public fun isValid(): Boolean {
            return apiKey.isNotBlank() && flushQueueSize > 0 && flushIntervalMillis > 0 && isMinIdLengthValid()
        }

        private fun isMinIdLengthValid(): Boolean {
            return minIdLength ?. let {
                it > 0
            } ?: let {
                true
            }
        }

        /**
         * Whether the upload request body should be gzip-compressed.
         * Custom server URLs may not support gzip, so compression is only enabled
         * for custom URLs when [enableRequestBodyCompression] is explicitly set to true.
         */
        public fun shouldCompressUploadBody(): Boolean {
            return if (!serverUrl.isNullOrBlank()) enableRequestBodyCompression else true
        }

        public fun getApiHost(): String {
            return this.serverUrl?.takeIf { it.isNotBlank() } ?: with(this) {
                when {
                    serverZone == ServerZone.EU && useBatch -> Constants.EU_BATCH_API_HOST
                    serverZone == ServerZone.EU -> Constants.EU_DEFAULT_API_HOST
                    useBatch -> Constants.BATCH_API_HOST
                    else -> Constants.DEFAULT_API_HOST
                }
            }
        }
    }

public enum class ServerZone {
    US,
    EU,
}
