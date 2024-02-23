package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.id.utilities.KeyValueStore
import com.amplitude.id.utilities.createDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

class EventsFileManager(
    private val directory: File,
    private val storageKey: String,
    private val kvs: KeyValueStore,
    private val logger: Logger
) {
    private val fileIndexKey = "amplitude.events.file.index.$storageKey"
    private val storageVersionKey = "amplitude.events.file.version.$storageKey"
    val filePathSet: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    val curFile: MutableMap<String, File> = ConcurrentHashMap<String, File>()

    companion object {
        const val MAX_FILE_SIZE = 975_000 // 975KB
        val writeMutexMap = ConcurrentHashMap<String, Mutex>()
        val readMutexMap = ConcurrentHashMap<String, Mutex>()
    }

    val writeMutex = writeMutexMap.getOrPut(storageKey) { Mutex() }
    private val readMutex = readMutexMap.getOrPut(storageKey) { Mutex() }

    init {
        createDirectory(directory)
        handleV1Files()
    }

    /**
     * closes existing file, if at capacity
     * opens a new file, if current file is full or uncreated
     * stores the event
     */
    suspend fun storeEvent(event: String) = writeMutex.withLock {
        var file = currentFile()
        if (!file.exists()) {
            // create it
            file.createNewFile()
        }

        // check if file is at capacity
        while (file.length() > MAX_FILE_SIZE) {
            finish(file)
            // update index
            file = currentFile()
            if (!file.exists()) {
                // create it
                file.createNewFile()
            }
        }

        val contents = "${event}\n"
        writeToFile(contents.toByteArray(), file)
    }

    private fun incrementFileIndex(): Boolean {
        val index = kvs.getLong(fileIndexKey, 0)
        return kvs.putLong(fileIndexKey, index + 1)
    }

    /**
     * Returns a comma-separated list of file paths that are not yet uploaded
     */
    fun read(): List<String> {
        // we need to filter out .temp file, since it's operating on the writing thread
        val fileList = directory.listFiles { _, name ->
            name.contains(storageKey) && !name.endsWith(".tmp") && !name.endsWith(".properties")
        } ?: emptyArray()
        return fileList.sortedBy { it ->
            getSortKeyForFile(it)
        }.map {
            it.absolutePath
        }
    }

    /**
     * deletes the file at filePath
     */
    fun remove(filePath: String): Boolean {
        filePathSet.remove(filePath)
        return File(filePath).delete()
    }

    private fun start(file: File) {
        // start batch object and events array
        val contents = """["""
        writeToFile(contents.toByteArray(), file)
    }

    /**
     * closes current file, and increase the index
     * so next write go to a new file
     */
    suspend fun rollover() = writeMutex.withLock {
        val file = currentFile()
        if (file.exists() && file.length() > 0) {
            finish(file)
        }
    }

    /**
     * Split one file to two smaller file
     * This is used to handle payload too large error response
     */
    fun splitFile(filePath: String, events: JSONArray) {
        val originalFile = File(filePath)
        if (!originalFile.exists()) {
            return
        }
        val fileName = originalFile.name
        val firstHalfFile = File(directory, "$fileName-1.tmp")
        val secondHalfFile = File(directory, "$fileName-2.tmp")
        val splitStrings = events.split()
        writeEventsToFile(splitStrings.first, firstHalfFile)
        writeEventsToFile(splitStrings.second, secondHalfFile)
        this.remove(filePath)
    }

    suspend fun getEventString(filePath: String): String = readMutex.withLock {
        // Block one time of file reads if another task has read the content of this file
        if (filePathSet.contains(filePath)) {
            filePathSet.remove(filePath)
            return ""
        }
        filePathSet.add(filePath)
        File(filePath).bufferedReader().use<BufferedReader, String> {
            val content = it.readText()
            val isCurrentVersion = content.endsWith("\n")
            if (isCurrentVersion) {
                // handle current version
                val events = JSONArray()
                content.split("\n").forEach {
                    if (it.isNotEmpty()) {
                        try {
                            events.put(JSONObject(it))
                        } catch (e: JSONException) {
                            logger.error("Failed to parse event: $it")
                        }
                    }
                }
                return if (events.length() > 0) {
                    events.toString()
                } else {
                    ""
                }
            } else {
                // handle earlier versions
                val normalizedContent = "[${content.trimStart('[', ',').trimEnd(']', ',')}]"
                try {
                    val jsonArray = JSONArray(normalizedContent)
                    return jsonArray.toString()
                } catch (e: JSONException) {
                    logger.error("Failed to parse events: $normalizedContent, dropping file: $filePath")
                    this.remove(filePath)
                    return ""
                }
            }
        }
    }

    fun release(filePath: String) {
        filePathSet.remove(filePath)
    }

    private fun finish(file: File?) {
        if (file == null || !file.exists()) {
            // if tmp file doesn't exist or empty then we don't need to do anything
            return
        }
        val fileWithoutExtension = file.nameWithoutExtension
        val finishedFile = File(directory, "$fileWithoutExtension")
        if (finishedFile.exists()) {
            logger.debug("File already exists: $finishedFile, handle gracefully.")
            // if the file already exists, race condition detected and  rename the current file to a new name to avoid collision
            val newName = "$fileWithoutExtension-${System.currentTimeMillis()}-${Random().nextInt(1000)}"
            file.renameTo(File(directory, newName))
            return
        } else {
            file.renameTo(File(directory, file.nameWithoutExtension))
        }
        incrementFileIndex()
        reset()
    }

    // return the current tmp file
    private fun currentFile(): File {
        val file = curFile[storageKey] ?: run {
            // check leftover tmp file
            val fileList = directory.listFiles { _, name ->
                name.contains(storageKey) && name.endsWith(".tmp")
            } ?: emptyArray()

            fileList.getOrNull(0)
        }
        val index = kvs.getLong(fileIndexKey, 0)
        curFile[storageKey] = file ?: File(directory, "$storageKey-$index.tmp")
        return curFile[storageKey]!!
    }

    private fun getSortKeyForFile(file: File): String {
        val name = file.nameWithoutExtension.replace("$storageKey-", "")
        val dashIndex = name.indexOf('-')
        if (dashIndex >= 0) {
            return name.substring(0, dashIndex).padStart(10, '0') + name.substring(dashIndex)
        }
        return name
    }

    // write to underlying file
    private fun writeToFile(content: ByteArray, file: File) {
        try {
            FileOutputStream(file, true).use {
                it.write(content)
                it.flush()
            }
        } catch (e: FileNotFoundException) {
            logger.error("File not found: ${file.absolutePath}")
        } catch (e: IOException) {
            logger.error("Failed to write to file: ${file.absolutePath}")
        } catch (e: SecurityException) {
            logger.error("Security exception when saving event: ${e.message}")
        }
    }

    private fun writeToFile(content: String, file: File) {
        file.createNewFile()
        FileOutputStream(file, true).use {
            it.write(content.toByteArray())
            it.flush()
        }
        file.renameTo(File(directory, file.nameWithoutExtension))
    }

    private fun writeEventsToFile(events: List<JSONObject>, file: File) {
        val contents = events.joinToString(separator = "\n", postfix = "\n") { it.toString() }
        writeToFile(contents, file)
    }

    private fun reset() {
        curFile.remove(storageKey)
    }

    private fun handleV1Files() {
        if (kvs.getLong(storageVersionKey, 1L) > 1L) {
            return
        }
        val unFinishedFiles = directory.listFiles { _, name ->
            name.contains(storageKey) && name.endsWith(".tmp") && !name.endsWith(".properties")
        } ?: emptyArray()
        unFinishedFiles.forEach {
            val content = it.readText()
            if (!content.endsWith("\n")) {
                // already in current format
                finish(it)
            }
        }
        kvs.putLong(storageVersionKey, 2)
    }
}
