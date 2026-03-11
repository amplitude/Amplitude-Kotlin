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
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IdentityStorageProvider
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Builder for creating Android [Configuration] instances without depending on the constructor
 * signature.
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

    fun build(): Configuration = ReadOnlyConfiguration(this)
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
 * An Android [Configuration] whose properties are read-only after construction.
 *
 * Mutating a read-only property logs a warning but still applies the value, so existing code
 * continues to work while surfacing that the mutation is unintended.
 *
 * Properties that are **mutable at runtime** (not overridden here) and remain freely settable:
 * - [offline] — toggled by network connectivity checks
 * - [optOut] — toggled by customers at runtime
 * - [trackingSessionEvents] — deprecated, has custom setter
 * - [defaultTracking] — deprecated, has custom setter
 */
internal class ReadOnlyConfiguration(
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
    // Core properties
    override var flushQueueSize: Int by readOnly(builder.flushQueueSize)
    override var flushIntervalMillis: Int by readOnly(builder.flushIntervalMillis)
    override var instanceName: String by readOnly(builder.instanceName)
    override var storageProvider: StorageProvider by readOnly(builder.storageProvider)
    override var loggerProvider: LoggerProvider by readOnly(builder.loggerProvider)
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

    // Android-specific properties
    override var useAdvertisingIdForDeviceId: Boolean by readOnly(builder.useAdvertisingIdForDeviceId)
    override var useAppSetIdForDeviceId: Boolean by readOnly(builder.useAppSetIdForDeviceId)
    override var newDeviceIdPerInstall: Boolean by readOnly(builder.newDeviceIdPerInstall)
    override var trackingOptions: TrackingOptions by readOnly(builder.trackingOptions)
    override var enableCoppaControl: Boolean by readOnly(builder.enableCoppaControl)
    override var locationListening: Boolean by readOnly(builder.locationListening)
    override var flushEventsOnClose: Boolean by readOnly(builder.flushEventsOnClose)
    override var minTimeBetweenSessionsMillis: Long by readOnly(builder.minTimeBetweenSessionsMillis)
    override var migrateLegacyData: Boolean by readOnly(builder.migrateLegacyData)
    override var interactionsOptions: InteractionsOptions by readOnly(builder.interactionsOptions)
    override var enableAutocaptureRemoteConfig: Boolean by readOnly(builder.enableAutocaptureRemoteConfig)

    // ── Mutable at runtime ──────────────────────────────────────────────────────
    // offline                — toggled by network connectivity checks
    // optOut                 — toggled by customers at runtime
    // trackingSessionEvents  — deprecated, has custom setter
    // defaultTracking        — deprecated, has custom setter
}
