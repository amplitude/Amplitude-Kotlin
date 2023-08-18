package com.amplitude.core.utilities

import com.amplitude.id.utilities.KeyValueStore
import com.amplitude.id.utilities.createDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class EventsFileManager(
    private val directory: File,
    private val storageKey: String,
    private val kvs: KeyValueStore
) {
    init {
        createDirectory(directory)
    }

    private val fileIndexKey = "amplitude.events.file.index.$storageKey"

    companion object {
        const val MAX_FILE_SIZE = 975_000 // 975KB
    }

    val writeMutex = Mutex()
    val readMutex = Mutex()
    val filePathSet: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    val curFile: MutableMap<String, File> = ConcurrentHashMap<String, File>()

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

        var contents = ""
        if (file.length() == 0L) {
            start(file)
        } else if (file.length() > 1) {
            contents += ","
        }
        contents += event
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
            name.contains(storageKey) && !name.endsWith(".tmp")
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
        writeToFile(splitStrings.first, firstHalfFile)
        writeToFile(splitStrings.second, secondHalfFile)
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
            return it.readText()
        }
    }

    fun release(filePath: String) {
        filePathSet.remove(filePath)
    }

    private fun finish(file: File?) {
        if (file == null || !file.exists() || file.length() == 0L) {
            // if tmp file doesn't exist or empty then we don't need to do anything
            return
        }
        // close events array and batch object
        val contents = """]"""
        writeToFile(contents.toByteArray(), file)
        file.renameTo(File(directory, file.nameWithoutExtension))
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
        FileOutputStream(file, true).use {
            it.write(content)
            it.flush()
        }
    }

    private fun writeToFile(content: String, file: File) {
        file.createNewFile()
        FileOutputStream(file).use {
            it.write(content.toByteArray())
            it.flush()
        }
        file.renameTo(File(directory, file.nameWithoutExtension))
    }

    private fun reset() {
        curFile.remove(storageKey)
    }
}
