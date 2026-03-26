package com.amplitude.android

import android.content.Context
import com.amplitude.android.storage.AndroidStorageContextV3
import com.amplitude.android.utilities.AndroidLoggerProvider
import com.amplitude.core.LoggerProvider
import com.amplitude.core.StorageProvider
import com.amplitude.id.IdentityStorageProvider

/**
 * Builder for creating Android [Configuration] instances without depending on the constructor
 * signature.
 *
 * This subtype preserves binary compatibility for previously compiled `Configuration` DSL lambdas:
 * the new builder-backed DSL still passes an object that is-a [Configuration].
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
    apiKey: String,
    context: Context,
) : Configuration(apiKey, context) {
    override var storageProvider: StorageProvider = AndroidStorageContextV3.eventsStorageProvider
    override var loggerProvider: LoggerProvider = AndroidLoggerProvider()
    override var identifyInterceptStorageProvider: StorageProvider = AndroidStorageContextV3.identifyInterceptStorageProvider
    override var identityStorageProvider: IdentityStorageProvider = AndroidStorageContextV3.identityStorageProvider

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
