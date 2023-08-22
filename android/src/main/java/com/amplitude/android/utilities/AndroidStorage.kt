package com.amplitude.android.utilities

import android.content.Context
import android.content.SharedPreferences
import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.EventsFileManager
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.FileResponseHandler
import com.amplitude.core.utilities.JSONUtil
import com.amplitude.core.utilities.ResponseHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import java.io.File

class AndroidStorage(
    context: Context,
    val storageKey: String,
    private val logger: Logger,
    internal val prefix: String?
) : Storage, EventsFileStorage {

    companion object {
        const val STORAGE_PREFIX = "amplitude-android"
    }

    internal val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("${getPrefix()}-$storageKey", Context.MODE_PRIVATE)
    private val storageDirectory: File = context.getDir(getDir(), Context.MODE_PRIVATE)
    private val eventsFile =
        EventsFileManager(storageDirectory, storageKey, AndroidKVS(sharedPreferences))
    private val eventCallbacksMap = mutableMapOf<String, EventCallBack>()

    override suspend fun writeEvent(event: BaseEvent) {
        eventsFile.storeEvent(JSONUtil.eventToString(event))
        event.callback?.let { callback ->
            event.insertId?.let {
                eventCallbacksMap.put(it, callback)
            }
        }
    }

    override suspend fun write(key: Storage.Constants, value: String) {
        sharedPreferences.edit().putString(key.rawVal, value).apply()
    }

    override suspend fun remove(key: Storage.Constants) {
        sharedPreferences.edit().remove(key.rawVal).apply()
    }

    override suspend fun rollover() {
        eventsFile.rollover()
    }

    override fun read(key: Storage.Constants): String? {
        return sharedPreferences.getString(key.rawVal, null)
    }

    override fun readEventsContent(): List<Any> {
        return eventsFile.read()
    }

    override fun releaseFile(filePath: String) {
        eventsFile.release(filePath)
    }

    override suspend fun getEventsString(content: Any): String {
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
            logger
        )
    }

    override fun removeFile(filePath: String): Boolean {
        return eventsFile.remove(filePath)
    }

    override fun getEventCallback(insertId: String): EventCallBack? {
        return eventCallbacksMap[insertId]
    }

    override fun removeEventCallback(insertId: String) {
        eventCallbacksMap.remove(insertId)
    }

    override fun splitEventFile(filePath: String, events: JSONArray) {
        eventsFile.splitFile(filePath, events)
    }

    private fun getPrefix(): String {
        return prefix ?: STORAGE_PREFIX
    }

    private fun getDir(): String {
        if (prefix != null) {
            return "$prefix-disk-queue"
        }
        return "amplitude-disk-queue"
    }
}

class AndroidStorageProvider : StorageProvider {
    override fun getStorage(amplitude: Amplitude, prefix: String?): Storage {
        val configuration = amplitude.configuration as com.amplitude.android.Configuration
        return AndroidStorage(
            configuration.context,
            configuration.instanceName,
            configuration.loggerProvider.getLogger(amplitude),
            prefix
        )
    }
}
