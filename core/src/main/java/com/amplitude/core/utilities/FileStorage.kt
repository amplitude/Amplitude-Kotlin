package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.http.HttpStatus
import com.amplitude.core.utilities.http.ResponseHandler
import com.amplitude.id.utilities.PropertiesFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import java.io.File

class FileStorage(
    storageKey: String,
    private val logger: Logger,
    private val prefix: String?,
    diagnostics: Diagnostics,
) : Storage, EventsFileStorage {
    companion object {
        const val STORAGE_PREFIX = "amplitude-kotlin"
    }

    private val storageDirectory = File("/tmp/${getPrefix()}/$storageKey")
    private val storageDirectoryEvents = File(storageDirectory, "events")

    private val propertiesFile = PropertiesFile(
        storageDirectory,
        "${getPrefix()}-$storageKey",
        null
    )
    private val eventsFile = EventsFileManager(
        storageDirectoryEvents,
        storageKey,
        propertiesFile,
        logger,
        diagnostics
    )
    private val eventCallbacksMap = mutableMapOf<String, EventCallBack>()

    init {
        propertiesFile.load()
    }

    override suspend fun writeEvent(event: BaseEvent) {
        eventsFile.storeEvent(JSONUtil.eventToString(event))
        event.callback?.let { callback ->
            event.insertId?.let {
                eventCallbacksMap.put(it, callback)
            }
        }
    }

    override suspend fun write(
        key: Storage.Constants,
        value: String,
    ) {
        propertiesFile.putString(key.rawVal, value)
    }

    override suspend fun remove(key: Storage.Constants) {
        propertiesFile.remove(key.rawVal)
    }

    override suspend fun rollover() {
        eventsFile.rollover()
    }

    override fun read(key: Storage.Constants): String? {
        return propertiesFile.getString(key.rawVal, null)
    }

    override fun readEventsContent(): List<Any> {
        // return List<String> list of file paths
        return eventsFile.read()
    }

    override fun releaseFile(filePath: String) {
        eventsFile.release(filePath)
    }

    override suspend fun getEventsString(filePath: Any): String {
        // content is filePath String
        return eventsFile.getEventString(filePath as String)
    }

    override fun getResponseHandler(
        eventPipeline: EventPipeline,
        configuration: Configuration,
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ): ResponseHandler {
        return FileResponseHandler(
            this,
            eventPipeline,
            configuration,
            scope,
            dispatcher,
            logger,
        )
    }

    override fun removeFile(filePath: String): Boolean {
        return eventsFile.remove(filePath)
    }

    override fun getEventCallback(insertId: String): EventCallBack? {
        return eventCallbacksMap.getOrDefault(insertId, null)
    }

    override fun removeEventCallback(insertId: String) {
        eventCallbacksMap.remove(insertId)
    }

    override fun splitEventFile(
        filePath: String,
        events: JSONArray,
    ) {
        eventsFile.splitFile(filePath, events)
    }

    private fun getPrefix(): String {
        return prefix ?: STORAGE_PREFIX
    }
}

class FileStorageProvider : StorageProvider {
    override fun getStorage(
        amplitude: Amplitude,
        prefix: String?,
    ): Storage {
        return FileStorage(
            amplitude.configuration.instanceName,
            amplitude.configuration.loggerProvider.getLogger(amplitude),
            prefix,
            amplitude.diagnostics,
        )
    }
}

interface EventsFileStorage {
    /**
     * Deletes the file at the given [filePath].
     */
    fun removeFile(filePath: String): Boolean

    /**
     * Get the [EventCallBack] for the given [insertId].
     *
     * @param insertId A random [java.util.UUID] associated with an event.
     */
    fun getEventCallback(insertId: String): EventCallBack?

    /**
     * Remove the [EventCallBack] for the given [insertId].
     *
     * @param insertId A random [java.util.UUID] associated with an event.
     */
    fun removeEventCallback(insertId: String)

    /**
     * Split the file at the given [filePath] into two files.
     * This is used to handle [HttpStatus.PAYLOAD_TOO_LARGE] response.
     */
    fun splitEventFile(
        filePath: String,
        events: JSONArray,
    )

    /**
     * Returns a list of file paths for all the Event files.
     *
     * NOTE: this function is shared with [Storage]
     */
    fun readEventsContent(): List<Any>

    /**
     * Releases the file at the given [filePath], this marks the end of a file read.
     */
    fun releaseFile(filePath: String)

    /**
     * Returns the Events JSON string content of the file at the given [filePath].
     *
     * NOTE: this function is shared with [Storage]
     */
    suspend fun getEventsString(filePath: Any): String

    /**
     * Closes the current file and increases the file index so the next write goes to a new file.
     *
     * NOTE: this function is shared with [Storage]
     */
    suspend fun rollover()
}
