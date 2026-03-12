package com.amplitude.android

import android.content.Context
import com.amplitude.android.storage.AndroidStorageContextV3
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.EventCallBack
import com.amplitude.core.LoggerProvider
import com.amplitude.core.ServerZone
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.frozen
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IdentityStorageProvider

/**
 * Builder for creating Android [Configuration] instances without depending on the constructor
 * signature.
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
 * val config = configuration("api-key", applicationContext) {
 *     autocapture = setOf(AutocaptureOption.SESSIONS)
 *     minTimeBetweenSessionsMillis = 10000
 * }
 * ```
 *
 * Java usage:
 * ```java
 * ConfigurationBuilder builder = new ConfigurationBuilder("api-key", context);
 * builder.setAutocapture(Collections.singleton(AutocaptureOption.SESSIONS));
 * Configuration config = builder.build();
 * ```
 */
class ConfigurationBuilder(
    internal val apiKey: String,
    internal val context: Context,
) {
    // Core properties (with Android-specific defaults)
    var flushQueueSize: Int = com.amplitude.core.Configuration.FLUSH_QUEUE_SIZE
    var flushIntervalMillis: Int = com.amplitude.core.Configuration.FLUSH_INTERVAL_MILLIS
    var instanceName: String = com.amplitude.core.Configuration.DEFAULT_INSTANCE
    var optOut: Boolean = false
    var storageProvider: StorageProvider = AndroidStorageContextV3.eventsStorageProvider
    var loggerProvider: LoggerProvider = AndroidLoggerProvider()
    var minIdLength: Int? = null
    var partnerId: String? = null
    var callback: EventCallBack? = null
    var flushMaxRetries: Int = com.amplitude.core.Configuration.FLUSH_MAX_RETRIES
    var useBatch: Boolean = false
    var serverZone: ServerZone = ServerZone.US
    var serverUrl: String? = null
    var plan: Plan? = null
    var ingestionMetadata: IngestionMetadata? = null
    var identifyBatchIntervalMillis: Long = com.amplitude.core.Configuration.IDENTIFY_BATCH_INTERVAL_MILLIS
    var identifyInterceptStorageProvider: StorageProvider = AndroidStorageContextV3.identifyInterceptStorageProvider
    var identityStorageProvider: IdentityStorageProvider = AndroidStorageContextV3.identityStorageProvider
    var offline: Boolean? = false
    var deviceId: String? = null
    var sessionId: Long? = null
    var httpClient: HttpClientInterface? = null
    var enableDiagnostics: Boolean = true
    var enableRequestBodyCompression: Boolean = false

    // Android-specific properties
    var useAdvertisingIdForDeviceId: Boolean = false
    var useAppSetIdForDeviceId: Boolean = false
    var newDeviceIdPerInstall: Boolean = false
    var trackingOptions: TrackingOptions = TrackingOptions()
    var enableCoppaControl: Boolean = false
    var locationListening: Boolean = false
    var flushEventsOnClose: Boolean = true
    var minTimeBetweenSessionsMillis: Long = Configuration.MIN_TIME_BETWEEN_SESSIONS_MILLIS
    var autocapture: Set<AutocaptureOption> = setOf(AutocaptureOption.SESSIONS)
    var migrateLegacyData: Boolean = true
    var interactionsOptions: InteractionsOptions = InteractionsOptions()
    var enableAutocaptureRemoteConfig: Boolean = true

    fun build(): Configuration = FrozenConfiguration(this)
}

/**
 * Creates an Android [Configuration] using a builder DSL.
 *
 * ```
 * val config = configuration("api-key", applicationContext) {
 *     autocapture = setOf(AutocaptureOption.SESSIONS)
 *     minTimeBetweenSessionsMillis = 10000
 * }
 * ```
 */
fun configuration(
    apiKey: String,
    context: Context,
    block: ConfigurationBuilder.() -> Unit = {},
): Configuration {
    return ConfigurationBuilder(apiKey, context).apply(block).build()
}

/**
 * An Android [Configuration] whose properties are frozen after construction.
 *
 * Mutating a frozen property logs a warning and ignores the new value.
 *
 * Properties that are **mutable at runtime** (not overridden here) and remain freely settable:
 * - [offline] — toggled by network connectivity checks
 * - [optOut] — toggled by customers at runtime
 * - [trackingSessionEvents] — deprecated, has custom setter
 * - [defaultTracking] — deprecated, has custom setter
 */
internal class FrozenConfiguration(
    builder: ConfigurationBuilder,
) : Configuration(
        apiKey = builder.apiKey,
        context = builder.context,
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
        useAdvertisingIdForDeviceId = builder.useAdvertisingIdForDeviceId,
        useAppSetIdForDeviceId = builder.useAppSetIdForDeviceId,
        newDeviceIdPerInstall = builder.newDeviceIdPerInstall,
        trackingOptions = builder.trackingOptions,
        enableCoppaControl = builder.enableCoppaControl,
        locationListening = builder.locationListening,
        flushEventsOnClose = builder.flushEventsOnClose,
        minTimeBetweenSessionsMillis = builder.minTimeBetweenSessionsMillis,
        autocapture = builder.autocapture,
        identifyBatchIntervalMillis = builder.identifyBatchIntervalMillis,
        identifyInterceptStorageProvider = builder.identifyInterceptStorageProvider,
        identityStorageProvider = builder.identityStorageProvider,
        migrateLegacyData = builder.migrateLegacyData,
        offline = builder.offline,
        deviceId = builder.deviceId,
        sessionId = builder.sessionId,
        httpClient = builder.httpClient,
        interactionsOptions = builder.interactionsOptions,
        enableDiagnostics = builder.enableDiagnostics,
        enableRequestBodyCompression = builder.enableRequestBodyCompression,
        enableAutocaptureRemoteConfig = builder.enableAutocaptureRemoteConfig,
    ) {
    private val logger = ConsoleLogger()

    // ── Frozen after construction ───────────────────────────────────────────────
    // Core properties
    override var flushQueueSize: Int by frozen(builder.flushQueueSize, logger)
    override var flushIntervalMillis: Int by frozen(builder.flushIntervalMillis, logger)
    override var instanceName: String by frozen(builder.instanceName, logger)
    override var storageProvider: StorageProvider by frozen(builder.storageProvider, logger)
    override var loggerProvider: LoggerProvider by frozen(builder.loggerProvider, logger)
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

    // Android-specific properties
    override var useAdvertisingIdForDeviceId: Boolean by frozen(builder.useAdvertisingIdForDeviceId, logger)
    override var useAppSetIdForDeviceId: Boolean by frozen(builder.useAppSetIdForDeviceId, logger)
    override var newDeviceIdPerInstall: Boolean by frozen(builder.newDeviceIdPerInstall, logger)
    override var trackingOptions: TrackingOptions by frozen(builder.trackingOptions, logger)
    override var enableCoppaControl: Boolean by frozen(builder.enableCoppaControl, logger)
    override var locationListening: Boolean by frozen(builder.locationListening, logger)
    override var flushEventsOnClose: Boolean by frozen(builder.flushEventsOnClose, logger)
    override var minTimeBetweenSessionsMillis: Long by frozen(builder.minTimeBetweenSessionsMillis, logger)
    override var migrateLegacyData: Boolean by frozen(builder.migrateLegacyData, logger)
    override var interactionsOptions: InteractionsOptions by frozen(builder.interactionsOptions, logger)
    override var enableAutocaptureRemoteConfig: Boolean by frozen(builder.enableAutocaptureRemoteConfig, logger)

    // ── Mutable at runtime ──────────────────────────────────────────────────────
    // offline                — toggled by network connectivity checks
    // optOut                 — toggled by customers at runtime
    // trackingSessionEvents  — deprecated, has custom setter
    // defaultTracking        — deprecated, has custom setter
}
