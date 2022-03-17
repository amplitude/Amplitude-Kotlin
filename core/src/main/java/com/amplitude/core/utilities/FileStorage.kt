package com.amplitude.core.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.EventCallBack
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.id.utilities.PropertiesFile
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File

class FileStorage(
    private val apiKey: String
) : Storage {

    companion object {
        const val STORAGE_PREFIX = "amplitude-kotlin"
    }

    private val storageDirectory = File("/tmp/amplitude-kotlin/$apiKey")
    private val storageDirectoryEvents = File(storageDirectory, "events")

    internal val propertiesFile = PropertiesFile(storageDirectory, apiKey, STORAGE_PREFIX)
    internal val eventsFile = EventsFileManager(storageDirectoryEvents, apiKey, propertiesFile)
    val eventCallbacksMap = mutableMapOf<String, EventCallBack>()

    init {
        propertiesFile.load()
    }

    override suspend fun writeEvent(event: BaseEvent) {
        eventsFile.storeEvent(JSONUtil.eventToString(event))
        event.callback?.let{
            eventCallbacksMap.put(event.insertId, it)
        }
    }

    override suspend fun write(key: Storage.Constants, value: String) {
        propertiesFile.putString(key.rawVal, value)
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

    override fun getEventsString(content: Any): String {
        // content is filePath String
        val bufferedReader: BufferedReader = File(content as String).bufferedReader()
        bufferedReader.use {
            return it.readText()
        }
    }

    fun removeFile(filePath: String): Boolean {
        return eventsFile.remove(filePath)
    }

    fun getEventCallback(insertId: String): EventCallBack? {
        return eventCallbacksMap.getOrDefault(insertId, null)
    }

    fun removeEventCallback(insertId: String) {
        eventCallbacksMap.remove(insertId)
    }

    fun splitEventFile(filePath: String, events: JSONArray) {
        eventsFile.splitFile(filePath, events)
    }
}

class FileStorageProvider: StorageProvider {
    override fun getStorage(amplitude: Amplitude): Storage {
        return FileStorage(amplitude.configuration.apiKey)
    }
}
