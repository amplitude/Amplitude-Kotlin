package com.amplitude.core

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityStorageProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Builder for creating [Configuration] instances without depending on the constructor signature.
 *
 * Adding new properties with defaults does not change any method signature, preserving binary
 * compatibility for compiled dependents.
 *
 * The resulting [Configuration] is read-only: mutating properties that were set at construction
 * time will log a warning. Properties that are intended to be mutable at runtime (`offline`,
 * `optOut`) remain freely settable.
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

    fun build(): Configuration = ReadOnlyConfiguration(this)
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
 * A [Configuration] whose properties are read-only after construction.
 *
 * Mutating a read-only property logs a warning but still applies the value, so existing code
 * continues to work while surfacing that the mutation is unintended.
 *
 * Properties that are **mutable at runtime** (not overridden here) and remain freely settable:
 * - [offline] — toggled by network connectivity checks
 * - [optOut] — toggled by customers at runtime
 */
internal class ReadOnlyConfiguration(
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

    private fun warnReadOnly(name: String) {
        logger.warn(
            "Property '$name' should not be modified after construction. " +
                "Use ConfigurationBuilder to set this value.",
        )
    }

    private fun <T> readOnly(initial: T): ReadWriteProperty<Any, T> =
        object : ReadWriteProperty<Any, T> {
            private var value = initial

            override fun getValue(
                thisRef: Any,
                property: KProperty<*>,
            ) = value

            override fun setValue(
                thisRef: Any,
                property: KProperty<*>,
                value: T,
            ) {
                warnReadOnly(property.name)
                this.value = value
            }
        }

    // ── Read-only after construction ────────────────────────────────────────────
    override var flushQueueSize: Int by readOnly(builder.flushQueueSize)
    override var flushIntervalMillis: Int by readOnly(builder.flushIntervalMillis)
    override var instanceName: String by readOnly(builder.instanceName)
    override var minIdLength: Int? by readOnly(builder.minIdLength)
    override var partnerId: String? by readOnly(builder.partnerId)
    override var callback: EventCallBack? by readOnly(builder.callback)
    override var flushMaxRetries: Int by readOnly(builder.flushMaxRetries)
    override var useBatch: Boolean by readOnly(builder.useBatch)
    override var serverZone: ServerZone by readOnly(builder.serverZone)
    override var serverUrl: String? by readOnly(builder.serverUrl)
    override var plan: Plan? by readOnly(builder.plan)
    override var ingestionMetadata: IngestionMetadata? by readOnly(builder.ingestionMetadata)
    override var identifyBatchIntervalMillis: Long by readOnly(builder.identifyBatchIntervalMillis)
    override var identifyInterceptStorageProvider: StorageProvider by readOnly(builder.identifyInterceptStorageProvider)
    override var identityStorageProvider: IdentityStorageProvider by readOnly(builder.identityStorageProvider)
    override var deviceId: String? by readOnly(builder.deviceId)
    override var sessionId: Long? by readOnly(builder.sessionId)
    override var httpClient: HttpClientInterface? by readOnly(builder.httpClient)
    override var enableDiagnostics: Boolean by readOnly(builder.enableDiagnostics)
    override var enableRequestBodyCompression: Boolean by readOnly(builder.enableRequestBodyCompression)

    // ── Mutable at runtime ──────────────────────────────────────────────────────
    // offline — toggled by network connectivity checks
    // optOut  — toggled by customers at runtime
}
