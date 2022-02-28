package com.amplitude.core.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.id.utilities.PropertiesFile
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

    init {
        propertiesFile.load()
    }

    override suspend fun writeEvent(event: BaseEvent) {
        eventsFile.storeEvent(JSONUtil.eventToString(event))
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

    override fun getEvents(): List<String> {
        val list = eventsFile.read()
        return list.map { path ->
            val bufferedReader: BufferedReader = File(path).bufferedReader()
            bufferedReader.use { it.readText() }
        }
    }

    fun removeFile(filePath: String): Boolean {
        return eventsFile.remove(filePath)
    }
}

class FileStorageProvider: StorageProvider {
    override fun getStorage(amplitude: Amplitude): Storage {
        return FileStorage(amplitude.configuration.apiKey)
    }
}