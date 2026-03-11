package com.amplitude.android

import android.content.Context
import com.amplitude.android.storage.AndroidStorageContextV3
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.core.EventCallBack
import com.amplitude.core.LoggerProvider
import com.amplitude.core.ServerZone
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IdentityStorageProvider

/**
 * Builder for creating Android [Configuration] instances without depending on the constructor
 * signature.
 *
 * Adding new properties with defaults does not change any method signature, preserving binary
 * compatibility for compiled dependents.
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
    private val apiKey: String,
    private val context: Context,
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

    fun build(): Configuration =
        Configuration(
            apiKey = apiKey,
            context = context,
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
            useAdvertisingIdForDeviceId = useAdvertisingIdForDeviceId,
            useAppSetIdForDeviceId = useAppSetIdForDeviceId,
            newDeviceIdPerInstall = newDeviceIdPerInstall,
            trackingOptions = trackingOptions,
            enableCoppaControl = enableCoppaControl,
            locationListening = locationListening,
            flushEventsOnClose = flushEventsOnClose,
            minTimeBetweenSessionsMillis = minTimeBetweenSessionsMillis,
            autocapture = autocapture,
            identifyBatchIntervalMillis = identifyBatchIntervalMillis,
            identifyInterceptStorageProvider = identifyInterceptStorageProvider,
            identityStorageProvider = identityStorageProvider,
            migrateLegacyData = migrateLegacyData,
            offline = offline,
            deviceId = deviceId,
            sessionId = sessionId,
            httpClient = httpClient,
            interactionsOptions = interactionsOptions,
            enableDiagnostics = enableDiagnostics,
            enableRequestBodyCompression = enableRequestBodyCompression,
            enableAutocaptureRemoteConfig = enableAutocaptureRemoteConfig,
        )
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
