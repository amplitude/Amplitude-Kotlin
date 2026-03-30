package com.amplitude.core

import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider

/**
 * Builder for creating [Configuration] instances without depending on the constructor signature.
 *
 * This subtype preserves binary compatibility for previously compiled `Configuration` DSL lambdas:
 * the new builder-backed DSL still passes an object that is-a [Configuration].
 *
 * Kotlin usage:
 * ```
 * Amplitude("api-key") {
 *     flushQueueSize = 50
 *     serverZone = ServerZone.EU
 * }
 * ```
 *
 * Java usage:
 * ```java
 * ConfigurationBuilder builder = new ConfigurationBuilder("api-key");
 * builder.setFlushQueueSize(50);
 * Amplitude amplitude = new Amplitude(builder.build());
 * ```
 */
class ConfigurationBuilder(
    apiKey: String,
) : Configuration(apiKey) {
    override var storageProvider: StorageProvider = InMemoryStorageProvider()
    override var loggerProvider: LoggerProvider = ConsoleLoggerProvider()

    fun build(): Configuration =
        Configuration(
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
