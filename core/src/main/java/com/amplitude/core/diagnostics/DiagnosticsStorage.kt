package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.utilities.Hash
import com.amplitude.core.utilities.toHexString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
) {
    private val sanitizedInstance: String = Hash.fnv1a64(instanceName).toHexString()

    companion object {
        private const val STORAGE_PREFIX = "com.amplitude.diagnostics"
        private const val TAGS_FILE = "tags.json"
        private const val COUNTERS_FILE = "counters.json"
        private const val HISTOGRAMS_FILE = "histograms.json"
        private const val EVENTS_FILE = "events.log"
        private const val MAX_EVENTS_LOG_BYTES = 256 * 1024
    }

    private sealed class Operation {
        data class SaveSnapshot(val snapshot: DiagnosticsSnapshot) : Operation()

        data object DeleteActiveFiles : Operation()
    }

    private val channel: Channel<Operation> = Channel(8192)

    private val actorJob: Job =
        coroutineScope.launch(storageIODispatcher) {
            while (isActive) {
                try {
                    when (val operation = channel.receive()) {
                        is Operation.SaveSnapshot -> {
                            val directory = activeStorageDirectory()
                            ensureDirectoryExists(directory)
                            persistSnapshot(operation.snapshot, directory)
                        }
                        is Operation.DeleteActiveFiles -> {
                            removeActiveFiles()
                        }
                    }
                } catch (e: Exception) {
                    logger.error("DiagnosticsStorage: Error processing operation: ${e.message}")
                }
            }
        }

    fun saveSnapshot(snapshot: DiagnosticsSnapshot) {
        channel.trySend(Operation.SaveSnapshot(snapshot))
    }

    fun deleteActiveFiles() {
        channel.trySend(Operation.DeleteActiveFiles)
    }

    /**
     * Load and clear data from previous sessions.
     * Do not nned to run from storage actor as it operates on different folders.
     */
    fun loadAndClearPreviousSessions(): List<DiagnosticsSnapshot> {
        val instanceDirectory = instanceDirectory()
        if (!instanceDirectory.exists() || !instanceDirectory.isDirectory) {
            return emptyList()
        }

        val snapshots = mutableListOf<DiagnosticsSnapshot>()
        val sessionDirs =
            instanceDirectory.listFiles { file ->
                file.isDirectory && file.name != sessionStartAt
            } ?: emptyArray()

        for (sessionDir in sessionDirs) {
            try {
                loadSnapshot(sessionDir)?.let { snapshots.add(it) }
            } catch (e: Exception) {
                logger.error("DiagnosticsStorage: Failed to load previous session ${sessionDir.name}: ${e.message}")
            } finally {
                deleteDirectory(sessionDir)
            }
        }
        return snapshots
    }

    private fun persistSnapshot(
        snapshot: DiagnosticsSnapshot,
        directory: File,
    ) {
        snapshot.tags?.let {
            try {
                persistTags(it, directory)
            } catch (e: IOException) {
                logger.error("DiagnosticsStorage: Failed to write tags: ${e.message}")
            }
        }

        snapshot.counters?.let {
            try {
                persistCounters(it, directory)
            } catch (e: IOException) {
                logger.error("DiagnosticsStorage: Failed to write counters: ${e.message}")
            }
        }

        snapshot.histograms?.let {
            try {
                persistHistograms(it, directory)
            } catch (e: IOException) {
                logger.error("DiagnosticsStorage: Failed to write histograms: ${e.message}")
            }
        }

        snapshot.events?.let {
            try {
                persistEvents(it, directory)
            } catch (e: IOException) {
                logger.error("DiagnosticsStorage: Failed to write events: ${e.message}")
            }
        }
    }

    private fun loadSnapshot(directory: File): DiagnosticsSnapshot? {
        val loadedTags = loadTags(File(directory, TAGS_FILE))
        val loadedCounters = loadCounters(File(directory, COUNTERS_FILE))
        val loadedHistograms = loadHistograms(File(directory, HISTOGRAMS_FILE))
        val loadedEvents = loadEventsFromDirectory(directory)

        if (loadedCounters.isEmpty() && loadedHistograms.isEmpty() && loadedEvents.isEmpty()) {
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

    private fun loadHistograms(file: File): Map<String, HistogramSnapshot> {
        if (!file.exists()) return emptyMap()
        return try {
            val json = readJsonFromFile(file)
            val result = mutableMapOf<String, HistogramSnapshot>()
            for (key in json.keys()) {
                result[key] = HistogramSnapshot.fromJSONObject(json.getJSONObject(key))
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

    private fun persistTags(
        tags: Map<String, String>,
        directory: File,
    ) {
        val json = JSONObject()
        for ((key, value) in tags) {
            json.put(key, value)
        }
        writeJsonToFile(File(directory, TAGS_FILE), json)
    }

    private fun persistCounters(
        counters: Map<String, Long>,
        directory: File,
    ) {
        val json = JSONObject()
        for ((key, value) in counters) {
            json.put(key, value)
        }
        writeJsonToFile(File(directory, COUNTERS_FILE), json)
    }

    private fun persistHistograms(
        histograms: Map<String, HistogramSnapshot>,
        directory: File,
    ) {
        val json = JSONObject()
        for ((key, value) in histograms) {
            json.put(key, value.toJSONObject())
        }
        writeJsonToFile(File(directory, HISTOGRAMS_FILE), json)
    }

    private fun persistEvents(
        events: List<DiagnosticsEvent>,
        directory: File,
    ) {
        val eventsLog = File(directory, EVENTS_FILE)
        prepareEventsLog(eventsLog, directory)
        appendEvents(events, eventsLog)
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
            }
        } catch (e: IOException) {
            logger.error("DiagnosticsStorage: Failed to write JSON to file: ${file.absolutePath}: ${e.message}")
            throw e
        } finally {
            tempFile.delete()
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

    /**
     * Removes the files from the active storage directory.
     * Tags file will not be removed.
     * This is called when the snapshot is flushed to the network.
     */
    private fun removeActiveFiles() {
        val directory = activeStorageDirectory()
        if (!directory.exists()) return

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

    private fun activeStorageDirectory(): File {
        return File(instanceDirectory(), sessionStartAt)
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
}
