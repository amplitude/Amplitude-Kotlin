package com.amplitude.core

import com.amplitude.core.Configuration.Companion.DEFAULT_INSTANCE
import com.amplitude.core.Configuration.Companion.FLUSH_INTERVAL_MILLIS
import com.amplitude.core.Configuration.Companion.FLUSH_MAX_RETRIES
import com.amplitude.core.Configuration.Companion.FLUSH_QUEUE_SIZE
import com.amplitude.core.Configuration.Companion.IDENTIFY_BATCH_INTERVAL_MILLIS
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityStorageProvider

/**
 * An immutable snapshot of SDK settings produced by [ConfigurationBuilder].
 *
 * All properties are `val` — the compiler prevents mutation after construction.
 * Pass this to [Amplitude] via `Amplitude(ImmutableConfiguration)`.
 *
 * [Amplitude.optOut] delegates to [Configuration.optOut] and remains mutable at runtime.
 * [Amplitude.offline] is read-only on [Amplitude]; set it via [Amplitude.configuration].
 */
class ImmutableConfiguration internal constructor(
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
    val serverUrl: String? = null,
    val plan: Plan? = null,
    val ingestionMetadata: IngestionMetadata? = null,
    val identifyBatchIntervalMillis: Long = IDENTIFY_BATCH_INTERVAL_MILLIS,
    val identifyInterceptStorageProvider: StorageProvider = InMemoryStorageProvider(),
    val identityStorageProvider: IdentityStorageProvider = IMIdentityStorageProvider(),
    val offline: Boolean? = false,
    val deviceId: String? = null,
    val sessionId: Long? = null,
    val httpClient: HttpClientInterface? = null,
    val enableDiagnostics: Boolean = true,
    val enableRequestBodyCompression: Boolean = false,
) {
    fun isValid(): Boolean =
        apiKey.isNotBlank() &&
            flushQueueSize > 0 &&
            flushIntervalMillis > 0 &&
            (minIdLength == null || minIdLength > 0)

    fun shouldCompressUploadBody(): Boolean = if (!serverUrl.isNullOrBlank()) enableRequestBodyCompression else true

    fun getApiHost(): String =
        serverUrl?.takeIf { it.isNotBlank() } ?: when {
            serverZone == ServerZone.EU && useBatch -> Constants.EU_BATCH_API_HOST
            serverZone == ServerZone.EU -> Constants.EU_DEFAULT_API_HOST
            useBatch -> Constants.BATCH_API_HOST
            else -> Constants.DEFAULT_API_HOST
        }

    fun toConfiguration(): Configuration =
        Configuration(
            apiKey = apiKey,
            flushQueueSize = flushQueueSize,
            flushIntervalMillis = flushIntervalMillis,
            instanceName = instanceName,
            optOut = optOut,
            storageProvider = storageProvider,
            loggerProvider = loggerProvider,
            minIdLength = minIdLength,
            partnerId = partnerId,
            callback = callback,
            flushMaxRetries = flushMaxRetries,
            useBatch = useBatch,
            serverZone = serverZone,
            serverUrl = serverUrl,
            plan = plan,
            ingestionMetadata = ingestionMetadata,
            identifyBatchIntervalMillis = identifyBatchIntervalMillis,
            identifyInterceptStorageProvider = identifyInterceptStorageProvider,
            identityStorageProvider = identityStorageProvider,
            offline = offline,
            deviceId = deviceId,
            sessionId = sessionId,
            httpClient = httpClient,
            enableDiagnostics = enableDiagnostics,
            enableRequestBodyCompression = enableRequestBodyCompression,
        )
}
