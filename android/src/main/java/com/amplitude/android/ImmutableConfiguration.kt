package com.amplitude.android

import android.content.Context
import com.amplitude.android.AutocaptureOption.SESSIONS
import com.amplitude.android.Configuration.Companion.MIN_TIME_BETWEEN_SESSIONS_MILLIS
import com.amplitude.android.storage.AndroidStorageContextV3
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.core.Configuration.Companion.DEFAULT_INSTANCE
import com.amplitude.core.Configuration.Companion.FLUSH_INTERVAL_MILLIS
import com.amplitude.core.Configuration.Companion.FLUSH_MAX_RETRIES
import com.amplitude.core.Configuration.Companion.FLUSH_QUEUE_SIZE
import com.amplitude.core.Configuration.Companion.IDENTIFY_BATCH_INTERVAL_MILLIS
import com.amplitude.core.Constants.BATCH_API_HOST
import com.amplitude.core.Constants.DEFAULT_API_HOST
import com.amplitude.core.Constants.EU_BATCH_API_HOST
import com.amplitude.core.Constants.EU_DEFAULT_API_HOST
import com.amplitude.core.EventCallBack
import com.amplitude.core.LoggerProvider
import com.amplitude.core.ServerZone
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.IngestionMetadata
import com.amplitude.core.events.Plan
import com.amplitude.core.utilities.http.HttpClientInterface
import com.amplitude.id.IdentityStorageProvider

/**
 * An immutable snapshot of Android SDK settings produced by [ConfigurationBuilder].
 *
 * All properties are `val` — the compiler prevents mutation after construction.
 * Pass this to [Amplitude] via `Amplitude(ImmutableConfiguration)`.
 *
 * Runtime-mutable state ([Amplitude.optOut], [Amplitude.offline]) is seeded from
 * [optOut] and [offline] at construction time, then managed on [Amplitude] directly.
 */
class ImmutableConfiguration internal constructor(
    val apiKey: String,
    val context: Context,
    // Core properties
    val flushQueueSize: Int = FLUSH_QUEUE_SIZE,
    val flushIntervalMillis: Int = FLUSH_INTERVAL_MILLIS,
    val instanceName: String = DEFAULT_INSTANCE,
    val optOut: Boolean = false,
    val storageProvider: StorageProvider = AndroidStorageContextV3.eventsStorageProvider,
    val loggerProvider: LoggerProvider = AndroidLoggerProvider(),
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
    val identifyInterceptStorageProvider: StorageProvider = AndroidStorageContextV3.identifyInterceptStorageProvider,
    val identityStorageProvider: IdentityStorageProvider = AndroidStorageContextV3.identityStorageProvider,
    val offline: Boolean? = false,
    val deviceId: String? = null,
    val sessionId: Long? = null,
    val httpClient: HttpClientInterface? = null,
    val enableDiagnostics: Boolean = true,
    val enableRequestBodyCompression: Boolean = false,
    // Android-specific properties
    val useAdvertisingIdForDeviceId: Boolean = false,
    val useAppSetIdForDeviceId: Boolean = false,
    val newDeviceIdPerInstall: Boolean = false,
    val trackingOptions: TrackingOptions = TrackingOptions(),
    val enableCoppaControl: Boolean = false,
    val locationListening: Boolean = false,
    val flushEventsOnClose: Boolean = true,
    val minTimeBetweenSessionsMillis: Long = MIN_TIME_BETWEEN_SESSIONS_MILLIS,
    val autocapture: Set<AutocaptureOption> = setOf(SESSIONS),
    val migrateLegacyData: Boolean = true,
    val interactionsOptions: InteractionsOptions = InteractionsOptions(),
    val enableAutocaptureRemoteConfig: Boolean = true,
) {
    fun isValid(): Boolean =
        com.amplitude.core.ConfigurationUtils.isValid(apiKey, flushQueueSize, flushIntervalMillis, minIdLength)

    fun shouldCompressUploadBody(): Boolean =
        com.amplitude.core.ConfigurationUtils.shouldCompressUploadBody(serverUrl, enableRequestBodyCompression)

    fun getApiHost(): String =
        com.amplitude.core.ConfigurationUtils.getApiHost(serverUrl, serverZone, useBatch)

    fun toConfiguration(): Configuration =
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
