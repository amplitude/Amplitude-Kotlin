package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.Constants
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.utilities.Sample
import com.amplitude.core.utilities.deepCopy
import com.amplitude.core.utilities.http.HttpClient
import com.amplitude.core.utilities.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.lang.ref.WeakReference
import kotlin.coroutines.EmptyCoroutineContext
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
    private val onClosed: () -> Unit = {},
) : DiagnosticsClient {
    private val storageDelegate = lazy {
        DiagnosticsStorage(
            storageDirectory = storageDirectory,
            instanceName = instanceName,
            sessionStartAt = startTimestampSeed,
            logger = logger,
            coroutineScope = coroutineScope,
            storageIODispatcher = storageIODispatcher,
        )
    }
    private val storage: DiagnosticsStorage by storageDelegate

    private val startTimestamp: Double = System.currentTimeMillis() / 1000.0
    private val startTimestampSeed: String by lazy { startTimestamp.toString() }
    private var sampleRate: Double = sampleRate.coerceIn(0.0, 1.0)

    private var shouldTrack: Boolean = false

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

    private val activeBuffer: Buffer by lazy { Buffer() }

    // RemoteConfigClient retains callbacks weakly, so keep this actor-created callback alive.
    @Volatile
    private var remoteConfigCallback: RemoteConfigClient.RemoteConfigCallback? = null

    init {
        try {
            // Dispatch before reading the scope's context or constructing any coroutine jobs.
            networkIODispatcher.dispatch(context = EmptyCoroutineContext) {
                try {
                    startActor()
                } catch (e: Exception) {
                    initializationFailed(e)
                }
            }
        } catch (e: Exception) {
            initializationFailed(e)
        }
    }

    private fun startActor() {
        val firstUpdate = channel.tryReceive()
        if (firstUpdate.isClosed) {
            onClosed()
            return
        }
        // We are already on the I/O worker; avoid another dispatch for initialization.
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                if (!isActive) return@launch
                try {
                    shouldTrack = enabled && Sample.isInSample(seed = startTimestampSeed, sampleRate = sampleRate)
                    subscribeToRemoteConfig()
                    registerShutdownHook()
                    initializeTasksIfNeeded()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("DiagnosticsClient: Error initializing diagnostics: ${e.message}")
                }
                var pendingUpdate = firstUpdate.getOrNull()
                while (isActive) {
                    try {
                        val initialUpdate = pendingUpdate
                        pendingUpdate = null
                        if (initialUpdate != null) {
                            handleUpdate(initialUpdate)
                            continue
                        }
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
            } finally {
                remoteConfigCallback = null
                channel.close()
                if (storageDelegate.isInitialized()) {
                    storage.close()
                }
                onClosed()
            }
        }
    }

    private fun subscribeToRemoteConfig() {
        val remoteConfigClient = remoteConfigClient ?: return
        val initialUpdates = mutableListOf<Update.UpdateConfig>()
        var subscribing = true
        val callback = RemoteConfigClient.RemoteConfigCallback { config, _, _ ->
            if (remoteConfigCallback == null) return@RemoteConfigCallback
            val enabledConfig = config?.get("enabled") as? Boolean
            val sampleRateConfig = (config?.get("sampleRate") as? Number)?.toDouble()
            val update = Update.UpdateConfig(enabled = enabledConfig, sampleRate = sampleRateConfig)
            logger.debug("DiagnosticsClient: Did fetch remote config with sampleRate: $sampleRateConfig")
            synchronized(initialUpdates) {
                if (subscribing) {
                    initialUpdates.add(update)
                } else {
                    channel.trySend(update)
                }
            }
        }
        remoteConfigCallback = callback
        try {
            remoteConfigClient.subscribe(RemoteConfigClient.Key.Diagnostics, callback = requireNotNull(remoteConfigCallback))
        } finally {
            synchronized(initialUpdates) { subscribing = false }
            // Apply configuration delivered during subscribe before processing early user calls.
            initialUpdates.forEach { handleUpdate(it) }
            initialUpdates.clear()
        }
    }

    private fun initializationFailed(exception: Exception) {
        channel.close()
        onClosed()
        logger.warn("DiagnosticsClient: Unable to start diagnostics: ${exception.message}")
    }

    /**
     * Closes the diagnostics client and allows the actor to finish processing queued operations.
     * The actor will exit gracefully after processing remaining items in the channel.
     * This is non-blocking; the storage will continue processing asynchronously.
     */
    override fun close() {
        channel.close()
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
        channel.trySend(Update.SetTags(tags.toMap()))
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
                eventProperties = properties?.deepCopy(),
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
        // Resolve storage on the diagnostics actor so close cannot race its first creation.
        val storage = storage
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
            handleUpdate(Update.Increment(key = "sampled.in.and.enabled", value = 1))
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

        handleUpdate(Update.SetTags(tags = staticContext))
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

        runCatchingCancellable {
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
                    body = listOf(jsonString),
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
                return@runCatchingCancellable
            }

            logger.debug("DiagnosticsClient: Uploaded diagnostics : $jsonString")
        }.onFailure { e ->
            logger.error("DiagnosticsClient: Failed to upload diagnostics: ${e.message}")
        }
    }

    private fun getDiagnosticsUrl(): String {
        return if (serverZone == ServerZone.EU) EU_DIAGNOSTICS_URL else US_DIAGNOSTICS_URL
    }
}
