package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.core.events.BaseEvent
import com.amplitude.id.utilities.createDirectory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

// Supports 2 event file versions:
// * version 1:
//      file path: BASEDIR/STORAGEKEY-INDEX, for example: BASEDIR/$default_instance-0, BASEDIR/$default_instance-1.tmp
//      internal format: single-line JSON event array
// * version 2 (current):
//      file path: BASEDIR/STORAGEKEY/TIMESTAMP-STORAGEID, for example: BASEDIR/$default_instance/1692717940123-abcdefg_hjklmno, BASEDIR/$default_instance/1692718000555-abcdefg_hjklmno.tmp
//      internal format: each line is a JSON event
class EventsFileManager(
    directory: File,
    private val storageKey: String,
    private val logger: Logger,
    private val getCurrentTimestamp: () -> Long = System::currentTimeMillis
) {
    private val directory = directory.resolve(storageKey)
    val id = "${ID}_${generateRandomString(INSTANCE_ID_LENGTH)}"
    private val idRegex = "-$id($|\\.|-)".toRegex()
    private var lastUsedTimestamp: Long = -1

    init {
        createDirectory(this.directory)
        attachPreviousV1Files()
        attachPreviousV2Files()
    }

    companion object {
        const val MAX_FILE_SIZE = 975_000 // 975KB
        private const val ID_LENGTH = 7
        private const val INSTANCE_ID_LENGTH = 7
        const val TIMESTAMP_SIZE = 13 // Size of unix timestamp in milliseconds.

        // Static part of storage Id - changed on each app run.
        val ID = generateRandomString(ID_LENGTH)

        private fun generateRandomString(length: Int): String {
            val allowedChars = ('a'..'z') + ('0'..'9')
            return (1..length)
                .map { allowedChars.random() }
                .joinToString("")
        }
    }

    private val writeMutex = Mutex()
    private var currentFile: File? = null

    /**
     * stores the event
     */
    suspend fun storeEvent(event: BaseEvent) = writeMutex.withLock {
        val file = currentFile()
        file.appendText("${JSONUtil.eventToString(event)}\n")
        if (file.length() > MAX_FILE_SIZE) {
            finish(file)
            currentFile = null
        }
    }

    /**
     * Returns a comma-separated list of file paths that are not yet uploaded
     */
    fun read(): List<String> {
        // we need to filter out .tmp file, since it's operating on the writing thread
        val fileList = directory.listFiles { _, name ->
            idRegex.containsMatchIn(name) && !name.endsWith(".tmp")
        } ?: emptyArray()
        return fileList.sortedBy { it ->
            it.name
        }.map {
            it.absolutePath
        }
    }

    /**
     * deletes the file at filePath
     */
    fun remove(filePath: String): Boolean {
        return File(filePath).delete()
    }

    /**
     * closes current file
     */
    suspend fun rollover() = writeMutex.withLock {
        val file = this.currentFile
        if (file != null) {
            finish(file)
            currentFile = null
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
        val firstHalfFile = File(originalFile.parent, "$fileName-1.tmp")
        val secondHalfFile = File(originalFile.parent, "$fileName-2.tmp")
        val splitStrings = events.split()
        writeEventsToFile(splitStrings.first, firstHalfFile)
        writeEventsToFile(splitStrings.second, secondHalfFile)
        this.finish(firstHalfFile)
        this.finish(secondHalfFile)
        this.remove(filePath)
    }

    fun getEventString(filePath: String): String {
        val content = File(filePath).readText()
        val isV1Content = content.startsWith("[") || content.endsWith("]") || content.endsWith(",")
        if (isV1Content) {
            val normalizedContent = "[${content.trimStart('[').trimEnd(']', ',')}]"
            return try {
                JSONArray(normalizedContent)
                normalizedContent
            } catch (e: JSONException) {
                logger.error("can't parse json events $normalizedContent: ${e.localizedMessage}")
                ""
            }
        } else {
            val events = JSONArray()
            val lines = content.split('\n')
            lines.forEach {
                if (it != "") {
                    try {
                        val event = JSONObject(it)
                        events.put(event)
                    } catch (e: JSONException) {
                        logger.error("can't parse json event $it: ${e.localizedMessage}")
                        // skip invalid event
                    }
                }
            }
            return if (events.length() > 0) events.toString() else ""
        }
    }

    private fun finish(file: File) {
        if (file.exists()) {
            file.renameTo(File(file.parent, file.nameWithoutExtension))
        }
    }

    // return the current tmp file
    private fun currentFile(): File {
        var file = currentFile
        if (file != null) {
            return file
        }

        var timestamp = getCurrentTimestamp()
        // Timestamps should be unique.
        if (timestamp <= lastUsedTimestamp) {
            timestamp = lastUsedTimestamp + 1
        }
        lastUsedTimestamp = timestamp
        file = File(directory, "${timestamp.toString().padStart(TIMESTAMP_SIZE, '0')}-$id.tmp")
        this.currentFile = file
        return file
    }

    private fun writeEventsToFile(events: List<JSONObject>, file: File) {
        val content = events.joinToString("\n", postfix = "\n") { it.toString() }
        file.writeText(content)
    }

    // Rename (change id part) and move version 1 files from parent directory (from previous app runs).
    private fun attachPreviousV1Files() {
        val fileList = directory.parentFile.listFiles { _, name ->
            name.startsWith("$storageKey-")
        } ?: emptyArray()
        fileList.forEach {
            val name = it.name.removePrefix("$storageKey-").removeSuffix(".tmp")
            val nameParts = name.split("-").toMutableList()
            nameParts[0] = nameParts[0].padStart(TIMESTAMP_SIZE, '0')
            nameParts.add(1, id)
            val newName = nameParts.joinToString("-")
            try {
                it.renameTo(File(directory, newName))
            } catch (e: Exception) {
                logger.error("can't rename ${it.absolutePath} to $newName: ${e.localizedMessage}")
            }
        }
    }

    // Rename (change id part) version 2 files (from previous app runs).
    private fun attachPreviousV2Files() {
        val previousFiles = directory.listFiles { _, name ->
            !name.contains("-$ID-")
        } ?: emptyArray()
        previousFiles.forEach {
            val nameParts = it.name.removeSuffix(".tmp").split("-").toMutableList()
            if (nameParts.size > 1) {
                nameParts[1] = id
            }
            val newName = nameParts.joinToString("-")
            try {
                it.renameTo(File(directory, newName))
            } catch (e: Exception) {
                logger.error("can't rename ${it.absolutePath} to $newName: ${e.localizedMessage}")
            }
        }
    }
}
