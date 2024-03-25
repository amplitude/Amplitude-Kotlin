package com.amplitude.core.utilities

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.id.utilities.PropertiesFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock

class FileStorage(
    storageKey: String,
    private val logger: Logger,
    private val prefix: String?,
    private val diagnostics: Diagnostics,
) : Storage, EventsFileStorage {
    companion object {
        const val STORAGE_PREFIX = "amplitude-kotlin"
    }

    private val storageDirectory = File("/tmp/${getPrefix()}/$storageKey")
    private val storageDirectoryEvents = File(storageDirectory, "events")

    private val propertiesFile = PropertiesFile(storageDirectory, storageKey, getPrefix(), null)
    private val eventsFile = EventsFileManager(storageDirectoryEvents, storageKey, propertiesFile, logger, diagnostics)
    private val eventCallbacksMap = mutableMapOf<String, EventCallBack>()

    val propertiesFileLock = ReentrantReadWriteLock()

    init {
        propertiesFile.load()
    }

    override suspend fun writeEvent(event: BaseEvent) {
        eventsFile.storeEvent(JSONUtil.eventToString(event))
        event.callback?.let { callback ->
            event.insertId?. let {
                eventCallbacksMap.put(it, callback)
            }
        }
    }

    override suspend fun write(
        key: Storage.Constants,
        value: String,
    ) {
        propertiesFileLock.writeLock().lock()
        propertiesFile.putString(key.rawVal, value)
        propertiesFileLock.writeLock().unlock()
    }

    override suspend fun remove(key: Storage.Constants) {
        propertiesFileLock.writeLock().lock()
        propertiesFile.remove(key.rawVal)
        propertiesFileLock.writeLock().unlock()
    }

    override suspend fun rollover() {
        eventsFile.rollover()
    }

    override fun read(key: Storage.Constants): String? {
        var value: String? = null
        propertiesFileLock.readLock().lock()
        value = propertiesFile.getString(key.rawVal, null)
        propertiesFileLock.readLock().unlock()

        return value
    }

    override fun readEventsContent(): List<Any> {
        // return List<String> list of file paths
        return eventsFile.read()
    }

    override fun releaseFile(filePath: String) {
        eventsFile.release(filePath)
    }

    override suspend fun getEventsString(content: Any): String {
        // content is filePath String
        return eventsFile.getEventString(content as String)
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
    fun removeFile(filePath: String): Boolean

    fun getEventCallback(insertId: String): EventCallBack?

    fun removeEventCallback(insertId: String)

    fun splitEventFile(
        filePath: String,
        events: JSONArray,
    )

    fun readEventsContent(): List<Any>

    fun releaseFile(filePath: String)

    suspend fun getEventsString(content: Any): String

    suspend fun rollover()
}
