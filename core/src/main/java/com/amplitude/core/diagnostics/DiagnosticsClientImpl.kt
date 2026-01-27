package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.Constants
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.utilities.Sample
import com.amplitude.core.utilities.http.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.round

/**
 * Client for collecting and uploading SDK diagnostics data.
 */
internal class DiagnosticsClientImpl(
    private val apiKey: String,
    private val serverZone: ServerZone,
    private val instanceName: String,
    private val storageDirectory: java.io.File,
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
) : DiagnosticsClient {
    private val storage: DiagnosticsStorage

    private val startTimestamp: Double = System.currentTimeMillis() / 1000.0
    private val startTimestampSeed: String = startTimestamp.toString()
    private var sampleRate: Double = sampleRate.coerceIn(0.0, 1.0)

    @Volatile private var shouldTrack: Boolean =
        enabled && Sample.isInSample(seed = startTimestampSeed, sampleRate = this.sampleRate)

    private val didSetBasicTags = AtomicBoolean(false)
    private val didFlushPreviousSession = AtomicBoolean(false)

    private var flushJob: Job? = null
    private val flushMutex = Mutex()

    companion object {
        private const val US_DIAGNOSTICS_URL = "https://diagnostics.prod.us-west-2.amplitude.com/v1/capture"
        private const val EU_DIAGNOSTICS_URL = "https://diagnostics.prod.eu-central-1.amplitude.com/v1/capture"
        private const val DEFAULT_FLUSH_INTERVAL_MILLIS = 300_000L
        private const val DEFAULT_SAMPLE_RATE: Double = 0.0
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
                shouldStore = shouldTrack,
            )

        remoteConfigClient?.subscribe(RemoteConfigClient.Key.DIAGNOSTICS) { config, _, _ ->
            val enabledConfig = config["enabled"] as? Boolean
            val sampleRateConfig = (config["sampleRate"] as? Number)?.toDouble()

            logger.debug("DiagnosticsClient: Did fetch remote config with sampleRate: $sampleRateConfig")

            coroutineScope.launch(storageIODispatcher) {
                updateConfig(
                    enabled = enabledConfig,
                    sampleRate = sampleRateConfig,
                )
            }
        }

        coroutineScope.launch(storageIODispatcher) {
            initializeTasksIfNeeded()
        }
    }

    override suspend fun setTag(
        name: String,
        value: String,
    ) {
        storage.setTag(name, value)
        startFlushTimerIfNeeded()
    }

    override suspend fun setTags(tags: Map<String, String>) {
        storage.setTags(tags)
        startFlushTimerIfNeeded()
    }

    override suspend fun increment(
        name: String,
        size: Int,
    ) {
        storage.increment(name, size)
        startFlushTimerIfNeeded()
    }

    override suspend fun recordHistogram(
        name: String,
        value: Double,
    ) {
        storage.recordHistogram(name, value)
        startFlushTimerIfNeeded()
    }

    override suspend fun recordEvent(
        name: String,
        properties: Map<String, Any>?,
    ) {
        storage.recordEvent(name, properties)
        startFlushTimerIfNeeded()
    }

    override suspend fun flush() =
        flushMutex.withLock {
            if (!shouldTrack || !storage.didChanged()) return@withLock

            val snapshot = storage.dumpAndClearCurrentSession()
            uploadSnapshot(snapshot)
        }

    suspend fun updateConfig(
        enabled: Boolean? = null,
        sampleRate: Double? = null,
    ) {
        if (enabled != null) {
            this.enabled = enabled
        }
        if (sampleRate != null) {
            this.sampleRate = sampleRate.coerceIn(0.0, 1.0)
        }

        shouldTrack =
            this.enabled && Sample.isInSample(seed = startTimestampSeed, sampleRate = this.sampleRate)
        storage.setShouldStore(shouldTrack)
        if (!shouldTrack) {
            stopFlushTimer()
        } else if (storage.didChanged()) {
            startFlushTimerIfNeeded()
        }
        coroutineScope.launch(storageIODispatcher) {
            initializeTasksIfNeeded()
        }
    }

    private suspend fun initializeTasksIfNeeded() =
        coroutineScope {
            val previous = async { flushPreviousSessions() }
            val basicTags = async { setupBasicDiagnosticsTags() }
            previous.await()
            basicTags.await()
        }

    private suspend fun flushPreviousSessions() {
        if (!enabled || !didFlushPreviousSession.compareAndSet(false, true)) return

        val historicSnapshots = storage.loadAndClearPreviousSessions()
        for (snapshot in historicSnapshots) {
            uploadSnapshot(snapshot)
        }
    }

    private suspend fun setupBasicDiagnosticsTags() {
        if (!didSetBasicTags.compareAndSet(false, true)) return

        increment(name = "sampled.in.and.enabled")

        val staticContext = mutableMapOf<String, String>()
        val contextInfo = contextProvider?.getContextInfo()
        staticContext["version_name"] = contextInfo?.appVersion ?: ""
        staticContext["device_manufacturer"] = contextInfo?.manufacturer ?: ""
        staticContext["device_model"] = contextInfo?.model ?: ""
        staticContext["os_name"] = contextInfo?.osName ?: ""
        staticContext["os_version"] = contextInfo?.osVersion ?: ""
        staticContext["platform"] = contextInfo?.platform ?: ""
        staticContext["sdk.${Constants.SDK_LIBRARY}.version"] = Constants.SDK_VERSION

        setTags(staticContext)
    }

    private suspend fun startFlushTimerIfNeeded() =
        flushMutex.withLock {
            if (!shouldTrack || flushJob != null) return@withLock

            flushJob =
                coroutineScope.launch(storageIODispatcher) {
                    try {
                        delay(flushIntervalMillis)
                        flush()
                    } finally {
                        markFlushTimerFinished()
                    }
                    restartFlushTimerIfNeeded()
                }
        }

    private suspend fun stopFlushTimer() =
        flushMutex.withLock {
            val job = flushJob
            flushJob = null
            job?.cancel()
        }

    private suspend fun markFlushTimerFinished() =
        flushMutex.withLock {
            flushJob = null
        }

    private suspend fun restartFlushTimerIfNeeded() {
        if (shouldTrack && storage.didChanged()) {
            startFlushTimerIfNeeded()
        }
    }

    private suspend fun uploadSnapshot(snapshot: DiagnosticsSnapshot) {
        val histogramResults =
            snapshot.histograms.mapValues { (_, stats) ->
                val summary = stats.snapshot()
                val avg =
                    if (summary.count > 0L) {
                        round((summary.sum / summary.count.toDouble()) * 100) / 100
                    } else {
                        0.0
                    }
                stats.toResult(avg)
            }

        val payload = DiagnosticsPayload.fromSnapshot(snapshot, histogramResults)

        withContext(networkIODispatcher) {
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
                    return@withContext
                }

                logger.debug("DiagnosticsClient: Uploaded diagnostics")
            } catch (e: Exception) {
                logger.error("DiagnosticsClient: Failed to upload diagnostics: ${e.message}")
            }
        }
    }

    private fun getDiagnosticsUrl(): String {
        return if (serverZone == ServerZone.EU) EU_DIAGNOSTICS_URL else US_DIAGNOSTICS_URL
    }
}
