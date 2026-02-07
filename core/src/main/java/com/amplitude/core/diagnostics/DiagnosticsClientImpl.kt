package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.Constants
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.utilities.Sample
import com.amplitude.core.utilities.http.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.lang.ref.WeakReference
import kotlin.math.min

/**
 * Client for collecting and uploading SDK diagnostics data.
 */
@OptIn(RestrictedAmplitudeFeature::class)
internal class DiagnosticsClientImpl(
    private val apiKey: String,
    private val serverZone: ServerZone,
    private val instanceName: String,
    private val storageDirectory: File,
    private val logger: Logger,
    private val coroutineScope: CoroutineScope,
    private val networkIODispatcher: CoroutineDispatcher,
    private val storageIODispatcher: CoroutineDispatcher,
    private val remoteConfigClient: RemoteConfigClient? = null,
    private val httpClient: HttpClient,
    private val contextProvider: DiagnosticsContextProvider? = null,
    private var enabled: Boolean = true,
    sampleRate: Double = DEFAULT_SAMPLE_RATE,
    private val flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
    private val storageIntervalMillis: Long = DEFAULT_STORAGE_INTERVAL_MILLIS,
) : DiagnosticsClient {
    private val storage: DiagnosticsStorage

    private val startTimestamp: Double = System.currentTimeMillis() / 1000.0
    private val startTimestampSeed: String = startTimestamp.toString()
    private var sampleRate: Double = sampleRate.coerceIn(0.0, 1.0)

    private var shouldTrack: Boolean =
        enabled && Sample.isInSample(seed = startTimestampSeed, sampleRate = this.sampleRate)

    private var didSetBasicTags: Boolean = false
    private var didFlushPreviousSession: Boolean = false

    companion object {
        private const val US_DIAGNOSTICS_URL = "https://diagnostics.prod.us-west-2.amplitude.com/v1/capture"
        private const val EU_DIAGNOSTICS_URL = "https://diagnostics.prod.eu-central-1.amplitude.com/v1/capture"
        private const val DEFAULT_FLUSH_INTERVAL_MILLIS = 300_000L
        private const val DEFAULT_STORAGE_INTERVAL_MILLIS = 1_000L
        private const val DEFAULT_SAMPLE_RATE: Double = 0.0
        private const val MAX_EVENT_COUNT_PER_FLUSH = 10
    }

    private var storageAtMs: Long? = null
    private var flushAtMs: Long? = null

    private sealed class Update {
        data class SetTag(val key: String, val value: String) : Update()

        data class SetTags(val tags: Map<String, String>) : Update()

        data class Increment(val key: String, val value: Long) : Update()

        data class RecordHistogram(val key: String, val value: Double) : Update()

        data class AppendEvent(val event: DiagnosticsEvent) : Update()

        data class UpdateConfig(val enabled: Boolean?, val sampleRate: Double?) : Update()

        data object InitializeTasks : Update()

        data object Flush : Update()
    }

    private class Buffer {
        val tags: MutableMap<String, String> = mutableMapOf()
        val counters: MutableMap<String, Long> = mutableMapOf()
        val histograms: MutableMap<String, HistogramStats> = mutableMapOf()
        val events: ArrayDeque<DiagnosticsEvent> = ArrayDeque()
        val addedEvents: ArrayDeque<DiagnosticsEvent> = ArrayDeque()

        var tagsChanged: Boolean = false
        var countersChanged: Boolean = false
        var histogramsChanged: Boolean = false

        fun needsStorage(): Boolean = tagsChanged || countersChanged || histogramsChanged || addedEvents.isNotEmpty()

        fun needsFlush(): Boolean = counters.isNotEmpty() || histograms.isNotEmpty() || events.isNotEmpty()

        fun createStorageSnapshot(): DiagnosticsSnapshot {
            val snapshot =
                DiagnosticsSnapshot(
                    tags = if (tagsChanged) tags.toMap() else null,
                    counters = if (countersChanged) counters.toMap() else null,
                    histograms = if (histogramsChanged) histograms.mapValues { (_, stats) -> stats.toSnapshot() } else null,
                    events = if (addedEvents.isNotEmpty()) addedEvents.toList() else null,
                )

            addedEvents.clear()
            tagsChanged = false
            countersChanged = false
            histogramsChanged = false

            return snapshot
        }

        fun createFlushSnapshot(): DiagnosticsSnapshot {
            val snapshot =
                DiagnosticsSnapshot(
                    tags = if (tags.isNotEmpty()) tags.toMap() else null,
                    counters = if (counters.isNotEmpty()) counters.toMap() else null,
                    histograms = if (histograms.isNotEmpty()) histograms.mapValues { (_, stats) -> stats.toSnapshot() } else null,
                    events = if (events.isNotEmpty()) events.toList() else null,
                )

            counters.clear()
            histograms.clear()
            events.clear()
            addedEvents.clear()
            countersChanged = false
            histogramsChanged = false
            tagsChanged = false

            return snapshot
        }
    }

    private val channel: Channel<Update> = Channel(Channel.UNLIMITED)

    private val activeBuffer = Buffer()

    // RemoteConfigClient uses WeakReference for callbacks, so we must hold a strong reference.
    private val remoteConfigCallback: RemoteConfigClient.RemoteConfigCallback? =
        if (remoteConfigClient != null) {
            RemoteConfigClient.RemoteConfigCallback { config, _, _ ->
                val enabledConfig = config["enabled"] as? Boolean
                val sampleRateConfig = (config["sampleRate"] as? Number)?.toDouble()

                logger.debug("DiagnosticsClient: Did fetch remote config with sampleRate: $sampleRateConfig")

                channel.trySend(Update.UpdateConfig(enabled = enabledConfig, sampleRate = sampleRateConfig))
            }
        } else {
            null
        }

    private val actorJob: Job =
        coroutineScope.launch {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    val nextTimeoutDelayMs = nextTimeoutDelayMs(now)
                    if (nextTimeoutDelayMs == null) {
                        val result = channel.receiveCatching()
                        if (result.isClosed) break
                        result.getOrNull()?.let { handleUpdate(it) }
                    } else {
                        val result = withTimeoutOrNull(nextTimeoutDelayMs) { channel.receiveCatching() }
                        when {
                            result == null -> handleTimeout()
                            result.isClosed -> break
                            else -> result.getOrNull()?.let { handleUpdate(it) }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("DiagnosticsClient: Error in actor loop: ${e.message}")
                }
            }
        }

    init {
        storage =
            DiagnosticsStorage(
                storageDirectory = storageDirectory,
                instanceName = instanceName,
                sessionStartAt = startTimestampSeed,
                logger = logger,
                coroutineScope = coroutineScope,
                storageIODispatcher = storageIODispatcher,
            )

        remoteConfigCallback?.let { callback ->
            remoteConfigClient?.subscribe(RemoteConfigClient.Key.DIAGNOSTICS, callback)
        }

        channel.trySend(Update.InitializeTasks)

        registerShutdownHook()
    }

    /**
     * Closes the diagnostics client and allows the actor to finish processing queued operations.
     * The actor will exit gracefully after processing remaining items in the channel.
     * This is non-blocking; the storage will continue processing asynchronously.
     */
    override fun close() {
        channel.close()
        storage.close()
    }

    private fun registerShutdownHook() {
        try {
            val weakRef = WeakReference(this)
            Runtime.getRuntime().addShutdownHook(
                object : Thread() {
                    override fun run() {
                        weakRef.get()?.close()
                    }
                },
            )
        } catch (_: Exception) {
            // Shutdown hook registration can fail due to:
            // - IllegalStateException: VM is already shutting down
            // - SecurityException: Security manager denies RuntimePermission("shutdownHooks")
            // - IllegalArgumentException: Hook already registered
            // None of these are critical since the shutdown hook is just for cleanup.
        }
    }

    override fun setTag(
        name: String,
        value: String,
    ) {
        channel.trySend(Update.SetTag(name, value))
    }

    override fun setTags(tags: Map<String, String>) {
        channel.trySend(Update.SetTags(tags))
    }

    override fun increment(
        name: String,
        size: Long,
    ) {
        channel.trySend(Update.Increment(name, size))
    }

    override fun recordHistogram(
        name: String,
        value: Double,
    ) {
        channel.trySend(Update.RecordHistogram(name, value))
    }

    override fun recordEvent(
        name: String,
        properties: Map<String, Any>?,
    ) {
        val event =
            DiagnosticsEvent(
                eventName = name,
                time = System.currentTimeMillis() / 1000.0,
                eventProperties = properties,
            )
        channel.trySend(Update.AppendEvent(event))
    }

    override fun flush() {
        channel.trySend(Update.Flush)
    }

    private fun initializeTasksIfNeeded() {
        flushPreviousSessions()
        setupBasicDiagnosticsTags()
    }

    /**
     * Load and clear previous sessions from storage and upload them to the network.
     * Do not need to check if upload is successful as it is not critical.
     */
    private fun flushPreviousSessions() {
        if (!enabled || didFlushPreviousSession) return
        didFlushPreviousSession = true

        val sampleRate = sampleRate
        coroutineScope.launch(storageIODispatcher) {
            val historicSnapshots = storage.loadAndClearPreviousSessions()
            for (snapshot in historicSnapshots) {
                coroutineScope.launch(networkIODispatcher) {
                    uploadSnapshot(snapshot, sampleRate)
                }
            }
        }
    }

    private fun setupBasicDiagnosticsTags() {
        if (didSetBasicTags) return
        didSetBasicTags = true

        if (shouldTrack) {
            increment(name = "sampled.in.and.enabled")
        }

        val contextInfo = contextProvider?.getContextInfo()
        val staticContext =
            buildMap {
                put("version_name", contextInfo?.appVersion ?: "")
                put("device_manufacturer", contextInfo?.manufacturer ?: "")
                put("device_model", contextInfo?.model ?: "")
                put("os_name", contextInfo?.osName ?: "")
                put("os_version", contextInfo?.osVersion ?: "")
                put("platform", contextInfo?.platform ?: "")
                put("sdk.${Constants.SDK_LIBRARY}.version", Constants.SDK_VERSION)
            }

        setTags(staticContext)
    }

    private fun handleUpdate(update: Update) {
        when (update) {
            is Update.SetTag -> {
                activeBuffer.tags[update.key] = update.value
                activeBuffer.tagsChanged = true
                ensureStorageDeadlineIfAllowed()
                ensureFlushDeadlineIfAllowed()
            }
            is Update.SetTags -> {
                activeBuffer.tags.putAll(update.tags)
                activeBuffer.tagsChanged = true
                ensureStorageDeadlineIfAllowed()
                ensureFlushDeadlineIfAllowed()
            }
            is Update.Increment -> {
                activeBuffer.counters[update.key] = (activeBuffer.counters[update.key] ?: 0L) + update.value
                activeBuffer.countersChanged = true
                ensureStorageDeadlineIfAllowed()
                ensureFlushDeadlineIfAllowed()
            }
            is Update.RecordHistogram -> {
                val histogram = activeBuffer.histograms.getOrPut(update.key) { HistogramStats() }
                histogram.record(update.value)
                activeBuffer.histogramsChanged = true
                ensureStorageDeadlineIfAllowed()
                ensureFlushDeadlineIfAllowed()
            }
            is Update.AppendEvent -> {
                if (activeBuffer.events.size < MAX_EVENT_COUNT_PER_FLUSH) {
                    activeBuffer.events.add(update.event)
                    activeBuffer.addedEvents.add(update.event)
                    ensureStorageDeadlineIfAllowed()
                    ensureFlushDeadlineIfAllowed()
                }
            }
            is Update.UpdateConfig -> {
                val oldEnabled = enabled
                val oldShouldTrack = shouldTrack

                enabled = update.enabled ?: enabled
                sampleRate = update.sampleRate?.coerceIn(0.0, 1.0) ?: sampleRate

                shouldTrack = enabled && Sample.isInSample(seed = startTimestampSeed, sampleRate = sampleRate)

                if (!oldEnabled && enabled) {
                    // false -> true
                    flushPreviousSessions()
                }

                if (!oldShouldTrack && shouldTrack) {
                    // false -> true
                    ensureStorageDeadlineIfAllowed()
                    ensureFlushDeadlineIfAllowed()
                } else if (oldShouldTrack && !shouldTrack) {
                    // true -> false
                    storageAtMs = null
                    flushAtMs = null
                }
            }
            is Update.Flush -> {
                flushActiveBuffer()
            }
            is Update.InitializeTasks -> {
                initializeTasksIfNeeded()
            }
        }
    }

    private fun handleTimeout() {
        val now = System.currentTimeMillis()

        val flushDue = flushAtMs?.let { it <= now } ?: false
        val storageDue = storageAtMs?.let { it <= now } ?: false

        if (flushDue) {
            flushAtMs = null
            flushActiveBuffer()
        }
        if (storageDue) {
            storageAtMs = null
            storeActiveBuffer()
        }
    }

    private fun nextTimeoutDelayMs(now: Long): Long? {
        val a = storageAtMs?.let { maxOf(0L, it - now) }
        val b = flushAtMs?.let { maxOf(0L, it - now) }
        return when {
            a == null && b == null -> null
            a == null -> b
            b == null -> a
            else -> min(a, b)
        }
    }

    private fun ensureStorageDeadlineIfAllowed() {
        if (!shouldTrack) return
        if (storageAtMs == null) {
            storageAtMs = System.currentTimeMillis() + storageIntervalMillis
        }
    }

    private fun ensureFlushDeadlineIfAllowed() {
        if (!shouldTrack) return

        if (flushAtMs == null) {
            flushAtMs = System.currentTimeMillis() + flushIntervalMillis
        }
    }

    private fun storeActiveBuffer() {
        if (!shouldTrack) return
        if (!activeBuffer.needsStorage()) return

        val snapshot = activeBuffer.createStorageSnapshot()

        storage.saveSnapshot(snapshot)
    }

    private fun flushActiveBuffer() {
        if (!shouldTrack) return
        if (!activeBuffer.needsFlush()) return

        val snapshot = activeBuffer.createFlushSnapshot()
        storage.deleteActiveFiles()
        val sampleRate = sampleRate

        coroutineScope.launch(networkIODispatcher) {
            uploadSnapshot(snapshot, sampleRate)
        }
    }

    private suspend fun uploadSnapshot(
        snapshot: DiagnosticsSnapshot,
        sampleRate: Double,
    ) {
        val payload = snapshot.toPayload()

        try {
            val jsonString = payload.toJsonString()
            val request =
                HttpClient.Request(
                    url = getDiagnosticsUrl(),
                    method = HttpClient.Request.Method.POST,
                    headers =
                        mapOf(
                            "X-ApiKey" to apiKey,
                            "X-Client-Sample-Rate" to sampleRate.toString(),
                        ),
                    body = jsonString,
                )
            val response = httpClient.request(request)
            if (!response.isSuccessful) {
                val responseBody = response.body
                if (!responseBody.isNullOrEmpty()) {
                    logger.error(
                        "DiagnosticsClient: Failed to upload diagnostics: ${response.statusCode}: $responseBody",
                    )
                } else {
                    logger.error("DiagnosticsClient: Failed to upload diagnostics: ${response.statusCode}")
                }
                return
            }

            logger.debug("DiagnosticsClient: Uploaded diagnostics : $jsonString")
        } catch (e: Exception) {
            logger.error("DiagnosticsClient: Failed to upload diagnostics: ${e.message}")
        }
    }

    private fun getDiagnosticsUrl(): String {
        return if (serverZone == ServerZone.EU) EU_DIAGNOSTICS_URL else US_DIAGNOSTICS_URL
    }
}
