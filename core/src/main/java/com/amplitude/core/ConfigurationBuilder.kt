package com.amplitude.core

import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityStorageProvider

/**
 * Builder for creating [ImmutableConfiguration] instances without depending on the constructor
 * signature.
 *
 * Adding new properties with defaults does not change any method signature, preserving binary
 * compatibility for compiled dependents.
 *
 * Kotlin usage:
 * ```
 * val config = configuration("api-key") {
 *     flushQueueSize = 50
 *     serverZone = ServerZone.EU
 * }
 * Amplitude(config)
 * ```
 *
 * Java usage:
 * ```java
 * ConfigurationBuilder builder = new ConfigurationBuilder("api-key");
 * builder.setFlushQueueSize(50);
 * Amplitude amplitude = new Amplitude(builder.build());
 * ```
 */
class ConfigurationBuilder(internal val apiKey: String) {
    var flushQueueSize: Int = Configuration.FLUSH_QUEUE_SIZE
    var flushIntervalMillis: Int = Configuration.FLUSH_INTERVAL_MILLIS
    var instanceName: String = Configuration.DEFAULT_INSTANCE
    var optOut: Boolean = false
    var storageProvider: StorageProvider = InMemoryStorageProvider()
    var loggerProvider: LoggerProvider = ConsoleLoggerProvider()
    var minIdLength: Int? = null
    var partnerId: String? = null
    var callback: EventCallBack? = null
    var flushMaxRetries: Int = Configuration.FLUSH_MAX_RETRIES
    var useBatch: Boolean = false
    var serverZone: ServerZone = ServerZone.US
    var serverUrl: String? = null
    var plan: Plan? = null
    var ingestionMetadata: IngestionMetadata? = null
    var identifyBatchIntervalMillis: Long = Configuration.IDENTIFY_BATCH_INTERVAL_MILLIS
    var identifyInterceptStorageProvider: StorageProvider = InMemoryStorageProvider()
    var identityStorageProvider: IdentityStorageProvider = IMIdentityStorageProvider()
    var offline: Boolean? = false
    var deviceId: String? = null
    var sessionId: Long? = null
    var httpClient: HttpClientInterface? = null
    var enableDiagnostics: Boolean = true
    var enableRequestBodyCompression: Boolean = false

    fun build(): ImmutableConfiguration =
        ImmutableConfiguration(
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

/**
 * Creates an [ImmutableConfiguration] using a builder DSL.
 *
 * ```
 * val config = configuration("api-key") {
 *     flushQueueSize = 50
 *     serverZone = ServerZone.EU
 * }
 * Amplitude(config)
 * ```
 */
fun configuration(
    apiKey: String,
    block: ConfigurationBuilder.() -> Unit = {},
): ImmutableConfiguration = ConfigurationBuilder(apiKey).apply(block).build()
