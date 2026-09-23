package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.remoteconfig.RemoteConfigClientImpl
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.http.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

/**
 * Interface for diagnostic tracking operations.
 * Provides methods to record tags, counters, histograms, and events
 * for SDK diagnostics and telemetry.
 */
@RestrictedAmplitudeFeature
public interface DiagnosticsClient {
    public companion object {
        /**
         * Creates a diagnostics client for use outside an [com.amplitude.core.Amplitude] instance.
         *
         * When [remoteConfigClient] is not provided, the client creates an analytics-core
         * remote config client and subscribes to `diagnostics.androidSDK`.
         */
        @RestrictedAmplitudeFeature
        @JvmOverloads
        public fun create(
            apiKey: String,
            serverZone: ServerZone,
            instanceName: String,
            storageDirectory: File,
            logger: Logger,
            remoteConfigClient: RemoteConfigClient? = null,
            diagnosticsContextProvider: DiagnosticsContextProvider? = null,
        ): DiagnosticsClient {
            val configuration =
                Configuration(
                    apiKey = apiKey,
                    instanceName = instanceName,
                    serverZone = serverZone,
                )
            val httpClient = HttpClient(configuration, logger)
            val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val resolvedRemoteConfigClient =
                remoteConfigClient ?: RemoteConfigClientImpl(
                    apiKey = apiKey,
                    serverZone = serverZone,
                    coroutineScope = coroutineScope,
                    networkIODispatcher = Dispatchers.IO,
                    storageIODispatcher = Dispatchers.IO,
                    storage = InMemoryStorage(),
                    httpClient = httpClient,
                    logger = logger,
                )

            val client =
                DiagnosticsClientImpl(
                    apiKey = apiKey,
                    serverZone = serverZone,
                    instanceName = instanceName,
                    storageDirectory = storageDirectory,
                    logger = logger,
                    coroutineScope = coroutineScope,
                    networkIODispatcher = Dispatchers.IO,
                    storageIODispatcher = Dispatchers.IO,
                    remoteConfigClient = resolvedRemoteConfigClient,
                    httpClient = httpClient,
                    contextProvider = diagnosticsContextProvider,
                )
            return object : DiagnosticsClient by client {
                override fun close() {
                    client.close()
                    coroutineScope.cancel()
                }
            }
        }
    }

    /**
     * Set a tag with the given name and value.
     * Tags are metadata labels associated with diagnostics data.
     *
     * @param name The tag name
     * @param value The tag value
     */
    public fun setTag(
        name: String,
        value: String,
    )

    /**
     * Set multiple tags at once.
     *
     * @param tags Map of tag names to values
     */
    public fun setTags(tags: Map<String, String>)

    /**
     * Increment a counter by the specified size.
     * Counters are numeric aggregates that accumulate over time.
     *
     * @param name The counter name
     * @param size The amount to increment (default 1)
     */
    public fun increment(
        name: String,
        size: Long = 1,
    )

    /**
     * Record a value for a histogram metric.
     * Histograms track distribution data (min, max, sum, count, average).
     *
     * @param name The histogram name
     * @param value The value to record
     */
    public fun recordHistogram(
        name: String,
        value: Double,
    )

    /**
     * Record a diagnostic event.
     *
     * @param name The event name
     * @param properties Optional properties map for the event
     */
    public fun recordEvent(
        name: String,
        properties: Map<String, Any>? = null,
    )

    /**
     * Flush all collected diagnostics data to the server.
     */
    public fun flush()

    /**
     * Close the diagnostics client and release resources.
     */
    public fun close()
}
