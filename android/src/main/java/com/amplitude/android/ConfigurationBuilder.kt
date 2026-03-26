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
import com.amplitude.core.Configuration as CoreConfiguration

/**
 * Builder for creating Android [Configuration] instances without depending on the constructor
 * signature.
 *
 * Adding new properties with defaults does not change any method signature, preserving binary
 * compatibility for compiled dependents.
 *
 * Kotlin usage:
 * ```
 * Amplitude("api-key", applicationContext) {
 *     autocapture = setOf(AutocaptureOption.SESSIONS)
 *     minTimeBetweenSessionsMillis = 10000
 * }
 * ```
 *
 * Java usage:
 * ```java
 * ConfigurationBuilder builder = new ConfigurationBuilder("api-key", context);
 * builder.setAutocapture(Collections.singleton(AutocaptureOption.SESSIONS));
 * Amplitude amplitude = new Amplitude(builder.build());
 * ```
 */
class ConfigurationBuilder(
    internal val apiKey: String,
    internal val context: Context,
) {
    // Core properties (with Android-specific defaults)
    var flushQueueSize: Int = CoreConfiguration.FLUSH_QUEUE_SIZE
    var flushIntervalMillis: Int = CoreConfiguration.FLUSH_INTERVAL_MILLIS
    var instanceName: String = CoreConfiguration.DEFAULT_INSTANCE
    var optOut: Boolean = false
    var storageProvider: StorageProvider = AndroidStorageContextV3.eventsStorageProvider
    var loggerProvider: LoggerProvider = AndroidLoggerProvider()
    var minIdLength: Int? = null
    var partnerId: String? = null
    var callback: EventCallBack? = null
    var flushMaxRetries: Int = CoreConfiguration.FLUSH_MAX_RETRIES
    var useBatch: Boolean = false
    var serverZone: ServerZone = ServerZone.US
    var serverUrl: String? = null
    var plan: Plan? = null
    var ingestionMetadata: IngestionMetadata? = null
    var identifyBatchIntervalMillis: Long = CoreConfiguration.IDENTIFY_BATCH_INTERVAL_MILLIS
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
            identifyBatchIntervalMillis = identifyBatchIntervalMillis,
            identifyInterceptStorageProvider = identifyInterceptStorageProvider,
            identityStorageProvider = identityStorageProvider,
            offline = offline,
            deviceId = deviceId,
            sessionId = sessionId,
            httpClient = httpClient,
            enableDiagnostics = enableDiagnostics,
            enableRequestBodyCompression = enableRequestBodyCompression,
            useAdvertisingIdForDeviceId = useAdvertisingIdForDeviceId,
            useAppSetIdForDeviceId = useAppSetIdForDeviceId,
            newDeviceIdPerInstall = newDeviceIdPerInstall,
            trackingOptions = trackingOptions,
            enableCoppaControl = enableCoppaControl,
            locationListening = locationListening,
            flushEventsOnClose = flushEventsOnClose,
            minTimeBetweenSessionsMillis = minTimeBetweenSessionsMillis,
            autocapture = autocapture,
            migrateLegacyData = migrateLegacyData,
            interactionsOptions = interactionsOptions,
            enableAutocaptureRemoteConfig = enableAutocaptureRemoteConfig,
        )
}
