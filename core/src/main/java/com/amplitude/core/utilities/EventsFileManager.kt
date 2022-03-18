package com.amplitude.core.utilities

import com.amplitude.id.utilities.KeyValueStore
import com.amplitude.id.utilities.createDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

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

    private var os: FileOutputStream? = null

    private var curFile: File? = null

    private val mutex = Mutex()

    companion object {
        const val MAX_FILE_SIZE = 975_000 // 975KB
    }

    /**
     * closes existing file, if at capacity
     * opens a new file, if current file is full or uncreated
     * stores the event
     */
    suspend fun storeEvent(event: String) = mutex.withLock {
        var newFile = false
        var file = currentFile()
        if (!file.exists()) {
            // create it
            file.createNewFile()
            start(file)
            newFile = true
        }

        // check if file is at capacity
        if (file.length() > MAX_FILE_SIZE) {
            finish()
            // update index
            file = currentFile()
            file.createNewFile()
            start(file)
            newFile = true
        }

        var contents = ""
        if (!newFile) {
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
        // we need to filter out .temp file, since its operating on the writing thread
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
    suspend fun rollover() = mutex.withLock {
        finish()
    }

    private fun finish() {
        val file = currentFile()
        if (!file.exists()) {
            // if tmp file doesnt exist then we dont need to do anything
            return
        }
        // close events array and batch object
        val contents = """]"""
        writeToFile(contents.toByteArray(), file)
        file.renameTo(File(directory, file.nameWithoutExtension))
        os?.close()
        incrementFileIndex()
        reset()
    }

    // return the current tmp file
    private fun currentFile(): File {
        curFile = curFile ?: run {
            val index = kvs.getLong(fileIndexKey, 0)
            File(directory, "$apiKey-$index.tmp")
        }

        return curFile!!
    }

    // write to underlying file
    private fun writeToFile(content: ByteArray, file: File) {
        os = os ?: FileOutputStream(file, true)
        os?.run {
            write(content)
            flush()
        }
    }

    private fun reset() {
        os = null
        curFile = null
    }

    private fun registerShutdownHook() {
        // close the stream if the app shuts down
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                os?.close()
            }
        })
    }
}
