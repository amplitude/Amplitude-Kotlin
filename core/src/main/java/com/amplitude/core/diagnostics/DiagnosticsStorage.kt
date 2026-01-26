package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.utilities.Hash
import com.amplitude.core.utilities.toHexString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Manages persistence of diagnostic data to disk.
 * Stores tags, counters, histograms, and events in separate files.
 *
 * File organization:
 * - {storageDirectory}/com.amplitude.diagnostics/{hashedInstanceName}/{sessionStartAt}/
 *   - tags.json
 *   - counters.json
 *   - histograms.json
 *   - events.log (newline-delimited JSON)
 */
internal class DiagnosticsStorage(
    private val storageDirectory: File,
    instanceName: String,
    private val sessionStartAt: String,
    private val logger: Logger,
    private val coroutineScope: CoroutineScope,
    private val storageIODispatcher: CoroutineDispatcher,
    private val persistIntervalMillis: Long = DEFAULT_PERSIST_INTERVAL_MILLIS,
    private var shouldStore: Boolean,
) {
    private val mutex = Mutex()

    // In-memory data
    private val tags = mutableMapOf<String, String>()
    private val counters = mutableMapOf<String, Long>()
    private val histograms = mutableMapOf<String, HistogramStats>()
    private val events = mutableListOf<DiagnosticsEvent>()

    private val unsavedEvents = mutableListOf<DiagnosticsEvent>()

    private var hasUnsavedTags = false
    private var hasUnsavedCounters = false
    private var hasUnsavedHistograms = false

    private var persistenceJob: Job? = null

    private val sanitizedInstance: String = Hash.fnv1a64(instanceName).toHexString()

    companion object {
        private const val STORAGE_PREFIX = "com.amplitude.diagnostics"
        private const val TAGS_FILE = "tags.json"
        private const val COUNTERS_FILE = "counters.json"
        private const val HISTOGRAMS_FILE = "histograms.json"
        private const val EVENTS_FILE = "events.log"
        private const val MAX_EVENT_COUNT_PER_PERSIST = 10
        private const val MAX_EVENT_COUNT_PER_FLUSH = 10
        private const val MAX_EVENTS_LOG_BYTES = 256 * 1024
        private const val DEFAULT_PERSIST_INTERVAL_MILLIS = 1_000L
    }

    suspend fun setShouldStore(shouldStore: Boolean) =
        mutex.withLock {
            this.shouldStore = shouldStore
            if (shouldStore) {
                startPersistenceTimerIfNeeded()
            } else {
                stopPersistenceTimer()
                try {
                    removeAllStoredFiles()
                } catch (e: Exception) {
                    logger.error("DiagnosticsStorage: Failed to remove files: ${e.message}")
                }
            }
        }

    suspend fun didChanged(): Boolean =
        mutex.withLock {
            counters.isNotEmpty() || histograms.isNotEmpty() || events.isNotEmpty()
        }

    suspend fun setTag(
        name: String,
        value: String,
    ) = mutex.withLock {
        tags[name] = value
        hasUnsavedTags = true
        startPersistenceTimerIfNeeded()
    }

    suspend fun setTags(newTags: Map<String, String>) =
        mutex.withLock {
            tags.putAll(newTags)
            hasUnsavedTags = true
            startPersistenceTimerIfNeeded()
        }

    suspend fun increment(
        name: String,
        size: Int,
    ) = mutex.withLock {
        val current = counters[name] ?: 0L
        counters[name] = current + size
        hasUnsavedCounters = true
        startPersistenceTimerIfNeeded()
    }

    suspend fun recordHistogram(
        name: String,
        value: Double,
    ) = mutex.withLock {
        val stats = histograms.getOrPut(name) { HistogramStats() }
        stats.record(value)
        hasUnsavedHistograms = true
        startPersistenceTimerIfNeeded()
    }

    suspend fun recordEvent(
        name: String,
        properties: Map<String, Any>?,
    ) = mutex.withLock {
        if (unsavedEvents.size >= MAX_EVENT_COUNT_PER_PERSIST ||
            events.size >= MAX_EVENT_COUNT_PER_FLUSH
        ) {
            logger.debug("DiagnosticsStorage: Event limit reached")
            return@withLock
        }
        val event =
            DiagnosticsEvent(
                eventName = name,
                time = System.currentTimeMillis() / 1000.0,
                eventProperties = properties,
            )
        events.add(event)
        unsavedEvents.add(event)
        startPersistenceTimerIfNeeded()
    }

    /**
     * Dump and clear current session data.
     * Tags are preserved after dump.
     */
    suspend fun dumpAndClearCurrentSession(): DiagnosticsSnapshot =
        mutex.withLock {
            val snapshotHistograms = copyHistograms(histograms)
            val snapshot =
                DiagnosticsSnapshot(
                    tags = tags.toMap(),
                    counters = counters.toMap(),
                    histograms = snapshotHistograms,
                    events = events.toList(),
                )

            counters.clear()
            histograms.clear()
            events.clear()
            unsavedEvents.clear()
            hasUnsavedCounters = false
            hasUnsavedHistograms = false

            try {
                removeFiles(includeTags = false)
            } catch (e: Exception) {
                logger.error("DiagnosticsStorage: Failed to remove files during dump: ${e.message}")
            }

            snapshot
        }

    /**
     * Load and clear data from previous sessions.
     */
    suspend fun loadAndClearPreviousSessions(): List<DiagnosticsSnapshot> =
        mutex.withLock {
            val instanceDirectory = instanceDirectory()
            if (!instanceDirectory.exists() || !instanceDirectory.isDirectory) {
                return@withLock emptyList()
            }

            val snapshots = mutableListOf<DiagnosticsSnapshot>()
            val sessionDirs =
                instanceDirectory.listFiles { file ->
                    file.isDirectory && file.name != sessionStartAt
                } ?: emptyArray()

            for (sessionDir in sessionDirs) {
                try {
                    loadSnapshot(sessionDir)?.let { snapshots.add(it) }
                    deleteDirectory(sessionDir)
                } catch (e: Exception) {
                    logger.error("DiagnosticsStorage: Failed to load previous session ${sessionDir.name}: ${e.message}")
                    deleteDirectory(sessionDir)
                }
            }

            snapshots
        }

    suspend fun persistIfNeeded() =
        mutex.withLock {
            if (!shouldStore) return@withLock

            if (hasUnsavedTags) {
                try {
                    val directory = storageDirectory()
                    persistTags(directory)
                    hasUnsavedTags = false
                } catch (e: IOException) {
                    logger.error("DiagnosticsStorage: Failed to write tags: ${e.message}")
                }
            }

            if (hasUnsavedCounters) {
                try {
                    val directory = storageDirectory()
                    persistCounters(directory)
                    hasUnsavedCounters = false
                } catch (e: IOException) {
                    logger.error("DiagnosticsStorage: Failed to write counters: ${e.message}")
                }
            }

            if (hasUnsavedHistograms) {
                try {
                    val directory = storageDirectory()
                    persistHistograms(directory)
                    hasUnsavedHistograms = false
                } catch (e: IOException) {
                    logger.error("DiagnosticsStorage: Failed to write histograms: ${e.message}")
                }
            }

            if (unsavedEvents.isNotEmpty()) {
                try {
                    val directory = storageDirectory()
                    val eventsLog = File(directory, EVENTS_FILE)
                    prepareEventsLog(eventsLog, directory)
                    appendEvents(unsavedEvents, eventsLog)
                    unsavedEvents.clear()
                } catch (e: IOException) {
                    logger.error("DiagnosticsStorage: Failed to add events: ${e.message}")
                }
            }
        }

    private fun copyHistograms(source: Map<String, HistogramStats>): Map<String, HistogramStats> {
        val copy = mutableMapOf<String, HistogramStats>()
        for ((key, stats) in source) {
            val statsCopy = HistogramStats()
            statsCopy.merge(stats)
            copy[key] = statsCopy
        }
        return copy
    }

    private fun loadSnapshot(directory: File): DiagnosticsSnapshot? {
        val loadedTags = loadTags(File(directory, TAGS_FILE))
        val loadedCounters = loadCounters(File(directory, COUNTERS_FILE))
        val loadedHistograms = loadHistograms(File(directory, HISTOGRAMS_FILE))
        val loadedEvents = loadEventsFromDirectory(directory)

        if (loadedTags.isEmpty() && loadedCounters.isEmpty() && loadedHistograms.isEmpty() && loadedEvents.isEmpty()) {
            return null
        }

        return DiagnosticsSnapshot(
            tags = loadedTags,
            counters = loadedCounters,
            histograms = loadedHistograms,
            events = loadedEvents,
        )
    }

    private fun loadTags(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return try {
            val json = readJsonFromFile(file)
            val result = mutableMapOf<String, String>()
            for (key in json.keys()) {
                result[key] = json.getString(key)
            }
            result
        } catch (e: Exception) {
            logger.error("DiagnosticsStorage: Failed to load tags: ${e.message}")
            emptyMap()
        }
    }

    private fun loadCounters(file: File): Map<String, Long> {
        if (!file.exists()) return emptyMap()
        return try {
            val json = readJsonFromFile(file)
            val result = mutableMapOf<String, Long>()
            for (key in json.keys()) {
                result[key] = json.getLong(key)
            }
            result
        } catch (e: Exception) {
            logger.error("DiagnosticsStorage: Failed to load counters: ${e.message}")
            emptyMap()
        }
    }

    private fun loadHistograms(file: File): Map<String, HistogramStats> {
        if (!file.exists()) return emptyMap()
        return try {
            val json = readJsonFromFile(file)
            val result = mutableMapOf<String, HistogramStats>()
            for (key in json.keys()) {
                result[key] = HistogramStats.fromJSONObject(json.getJSONObject(key))
            }
            result
        } catch (e: Exception) {
            logger.error("DiagnosticsStorage: Failed to load histograms: ${e.message}")
            emptyMap()
        }
    }

    private fun loadEvents(file: File): List<DiagnosticsEvent> {
        if (!file.exists()) return emptyList()
        return try {
            val result = mutableListOf<DiagnosticsEvent>()
            FileInputStream(file).use { fis ->
                BufferedReader(InputStreamReader(fis, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            if (it.isNotBlank()) {
                                try {
                                    val event = DiagnosticsEvent.fromJsonString(it)
                                    if (event != null) {
                                        result.add(event)
                                    } else {
                                        logger.error("DiagnosticsStorage: Skipping invalid event payload")
                                    }
                                } catch (e: JSONException) {
                                    logger.error("DiagnosticsStorage: Failed to parse event: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
            result
        } catch (e: Exception) {
            logger.error("DiagnosticsStorage: Failed to load events: ${e.message}")
            emptyList()
        }
    }

    private fun loadEventsFromDirectory(directory: File): List<DiagnosticsEvent> {
        if (!directory.exists() || !directory.isDirectory) return emptyList()

        val eventFiles =
            directory.listFiles { file ->
                file.isFile && (file.name == EVENTS_FILE || (file.name.startsWith("events-") && file.name.endsWith(".log")))
            }?.sortedBy { it.name } ?: emptyList()

        if (eventFiles.isEmpty()) return emptyList()

        val result = mutableListOf<DiagnosticsEvent>()
        for (file in eventFiles) {
            result.addAll(loadEvents(file))
        }
        return result
    }

    private fun persistTags(directory: File) {
        val json = JSONObject()
        for ((key, value) in tags) {
            json.put(key, value)
        }
        writeJsonToFile(File(directory, TAGS_FILE), json)
    }

    private fun persistCounters(directory: File) {
        val json = JSONObject()
        for ((key, value) in counters) {
            json.put(key, value)
        }
        writeJsonToFile(File(directory, COUNTERS_FILE), json)
    }

    private fun persistHistograms(directory: File) {
        val json = JSONObject()
        for ((key, stats) in histograms) {
            json.put(key, stats.toJSONObject())
        }
        writeJsonToFile(File(directory, HISTOGRAMS_FILE), json)
    }

    private fun prepareEventsLog(
        eventsFile: File,
        directory: File,
    ) {
        if (!eventsFile.exists()) {
            eventsFile.parentFile?.mkdirs()
            eventsFile.createNewFile()
            return
        }

        if (eventsFile.length() >= MAX_EVENTS_LOG_BYTES) {
            val timestamp = System.currentTimeMillis()
            val rotated = File(directory, "events-$timestamp.log")
            if (eventsFile.renameTo(rotated)) {
                eventsFile.createNewFile()
            } else {
                logger.warn("DiagnosticsStorage: Failed to rotate events log")
            }
        }
    }

    private fun appendEvents(
        events: List<DiagnosticsEvent>,
        file: File,
    ) {
        FileOutputStream(file, true).use { fos ->
            BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8)).use { writer ->
                for (event in events) {
                    writer.write(event.toJsonString())
                    writer.newLine()
                }
            }
        }
    }

    private fun writeJsonToFile(
        file: File,
        json: JSONObject,
    ) {
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(tempFile).use { fos ->
                BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8)).use { writer ->
                    writer.write(json.toString())
                }
            }
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: IOException) {
            tempFile.delete()
            throw e
        }
    }

    private fun readJsonFromFile(file: File): JSONObject {
        FileInputStream(file).use { fis ->
            BufferedReader(InputStreamReader(fis, Charsets.UTF_8)).use { reader ->
                val content = reader.readText()
                return JSONObject(content)
            }
        }
    }

    private fun removeAllStoredFiles() {
        removeFiles(includeTags = true)
    }

    private fun removeFiles(includeTags: Boolean) {
        val directory = File(instanceDirectory(), sessionStartAt)
        if (!directory.exists()) return

        if (includeTags) {
            File(directory, TAGS_FILE).delete()
        }
        File(directory, "${TAGS_FILE}.tmp").delete()
        File(directory, COUNTERS_FILE).delete()
        File(directory, "${COUNTERS_FILE}.tmp").delete()
        File(directory, HISTOGRAMS_FILE).delete()
        File(directory, "${HISTOGRAMS_FILE}.tmp").delete()
        File(directory, EVENTS_FILE).delete()

        directory.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("events-") && name.endsWith(".log")) {
                file.delete()
            }
        }
    }

    private fun storageDirectory(): File {
        val sessionDirectory = File(instanceDirectory(), sessionStartAt)
        ensureDirectoryExists(sessionDirectory)
        return sessionDirectory
    }

    private fun instanceDirectory(): File {
        return File(File(storageDirectory, STORAGE_PREFIX), sanitizedInstance)
    }

    private fun ensureDirectoryExists(directory: File) {
        if (!directory.exists()) {
            if (!directory.mkdirs() && !directory.exists()) {
                throw IOException("Failed to create directory: ${directory.absolutePath}")
            }
        }
    }

    private fun deleteDirectory(directory: File) {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
        }
        directory.delete()
    }

    // Callers must hold mutex.
    private fun startPersistenceTimerIfNeeded() {
        if (!shouldStore || persistenceJob != null || !hasUnsavedData()) return

        lateinit var job: Job
        job =
            coroutineScope.launch(storageIODispatcher) {
                try {
                    delay(persistIntervalMillis)
                    persistIfNeeded()
                } finally {
                    markPersistenceTimerFinished(job)
                }
            }
        persistenceJob = job
    }

    // Callers must hold mutex.
    private fun stopPersistenceTimer() {
        persistenceJob?.cancel()
        persistenceJob = null
    }

    private suspend fun markPersistenceTimerFinished(job: Job) =
        mutex.withLock {
            if (persistenceJob == job) {
                persistenceJob = null
            }
        }

    private fun hasUnsavedData(): Boolean {
        return hasUnsavedTags || hasUnsavedCounters || hasUnsavedHistograms || unsavedEvents.isNotEmpty()
    }
}
