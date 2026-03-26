package com.amplitude.android

import android.content.Context

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
) : Configuration(
        apiKey = apiKey,
        context = context,
        autocapture = setOf(AutocaptureOption.SESSIONS),
    ) {
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
