package com.amplitude.core.utilities

import com.amplitude.id.utilities.KeyValueStore
import com.amplitude.id.utilities.createDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class EventsFileManager(
    private val directory: File,
    private val apiKey: String,
    private val kvs: KeyValueStore
) {
    init {
        createDirectory(directory)
        registerShutdownHook()
    }

    private val fileIndexKey = "amplitude.events.file.index.$apiKey"

    companion object {
        const val MAX_FILE_SIZE = 975_000 // 975KB
        val writeMutex = Mutex()
        val readMutex = Mutex()
        val filePathSet = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        var curFile: File? = null
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
            name.contains(apiKey) && !name.endsWith(".tmp")
        } ?: emptyArray()
        return fileList.map {
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
        if (filePathSet.contains(filePath)) {
            return ""
        }
        filePathSet.add(filePath)
        File(filePath).bufferedReader().use {
            return it.readText()
        }
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
        curFile = curFile ?: run {
            val fileList = directory.listFiles { _, name ->
                name.contains(apiKey) && name.endsWith(".tmp")
            } ?: emptyArray()

            fileList.getOrNull(0)
        }
        val index = kvs.getLong(fileIndexKey, 0)
        return curFile ?: File(directory, "$apiKey-$index.tmp")
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
        curFile = null
    }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                finish(curFile)
            }
        })
    }
}
