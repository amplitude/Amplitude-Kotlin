package com.amplitude.core

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.frozen
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityStorageProvider

/**
 * Builder for creating [Configuration] instances without depending on the constructor signature.
 *
 * Adding new properties with defaults does not change any method signature, preserving binary
 * compatibility for compiled dependents.
 *
 * The resulting [Configuration] is frozen: mutating properties that were set at construction
 * time will log a warning and ignore the new value. Properties that are intended to be mutable
 * at runtime (`offline`, `optOut`) remain freely settable.
 *
 * Kotlin usage:
 * ```
 * val config = configuration("api-key") {
 *     flushQueueSize = 50
 *     serverZone = ServerZone.EU
 * }
 * ```
 *
 * Java usage:
 * ```java
 * ConfigurationBuilder builder = new ConfigurationBuilder("api-key");
 * builder.setFlushQueueSize(50);
 * Configuration config = builder.build();
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

    fun build(): Configuration = FrozenConfiguration(this)
}

/**
 * Creates a [Configuration] using a builder DSL.
 *
 * ```
 * val config = configuration("api-key") {
 *     flushQueueSize = 50
 *     serverZone = ServerZone.EU
 * }
 * ```
 */
fun configuration(
    apiKey: String,
    block: ConfigurationBuilder.() -> Unit = {},
): Configuration {
    return ConfigurationBuilder(apiKey).apply(block).build()
}

/**
 * A [Configuration] whose properties are frozen after construction.
 *
 * Mutating a frozen property logs a warning and ignores the new value.
 *
 * Properties that are **mutable at runtime** (not overridden here) and remain freely settable:
 * - [offline] — toggled by network connectivity checks
 * - [optOut] — toggled by customers at runtime
 */
internal class FrozenConfiguration(
    builder: ConfigurationBuilder,
) : Configuration(
        apiKey = builder.apiKey,
        flushQueueSize = builder.flushQueueSize,
        flushIntervalMillis = builder.flushIntervalMillis,
        instanceName = builder.instanceName,
        optOut = builder.optOut,
        storageProvider = builder.storageProvider,
        loggerProvider = builder.loggerProvider,
        minIdLength = builder.minIdLength,
        partnerId = builder.partnerId,
        callback = builder.callback,
        flushMaxRetries = builder.flushMaxRetries,
        useBatch = builder.useBatch,
        serverZone = builder.serverZone,
        serverUrl = builder.serverUrl,
        plan = builder.plan,
        ingestionMetadata = builder.ingestionMetadata,
        identifyBatchIntervalMillis = builder.identifyBatchIntervalMillis,
        identifyInterceptStorageProvider = builder.identifyInterceptStorageProvider,
        identityStorageProvider = builder.identityStorageProvider,
        offline = builder.offline,
        deviceId = builder.deviceId,
        sessionId = builder.sessionId,
        httpClient = builder.httpClient,
        enableDiagnostics = builder.enableDiagnostics,
        enableRequestBodyCompression = builder.enableRequestBodyCompression,
    ) {
    private val logger = ConsoleLogger()

    // ── Frozen after construction ───────────────────────────────────────────────
    override var flushQueueSize: Int by frozen(builder.flushQueueSize, logger)
    override var flushIntervalMillis: Int by frozen(builder.flushIntervalMillis, logger)
    override var instanceName: String by frozen(builder.instanceName, logger)
    override var minIdLength: Int? by frozen(builder.minIdLength, logger)
    override var partnerId: String? by frozen(builder.partnerId, logger)
    override var callback: EventCallBack? by frozen(builder.callback, logger)
    override var flushMaxRetries: Int by frozen(builder.flushMaxRetries, logger)
    override var useBatch: Boolean by frozen(builder.useBatch, logger)
    override var serverZone: ServerZone by frozen(builder.serverZone, logger)
    override var serverUrl: String? by frozen(builder.serverUrl, logger)
    override var plan: Plan? by frozen(builder.plan, logger)
    override var ingestionMetadata: IngestionMetadata? by frozen(builder.ingestionMetadata, logger)
    override var identifyBatchIntervalMillis: Long by frozen(builder.identifyBatchIntervalMillis, logger)
    override var identifyInterceptStorageProvider: StorageProvider by frozen(builder.identifyInterceptStorageProvider, logger)
    override var identityStorageProvider: IdentityStorageProvider by frozen(builder.identityStorageProvider, logger)
    override var deviceId: String? by frozen(builder.deviceId, logger)
    override var sessionId: Long? by frozen(builder.sessionId, logger)
    override var httpClient: HttpClientInterface? by frozen(builder.httpClient, logger)
    override var enableDiagnostics: Boolean by frozen(builder.enableDiagnostics, logger)
    override var enableRequestBodyCompression: Boolean by frozen(builder.enableRequestBodyCompression, logger)

    // ── Mutable at runtime ──────────────────────────────────────────────────────
    // offline — toggled by network connectivity checks
    // optOut  — toggled by customers at runtime
}
