package com.amplitude.android.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.amplitude.android.utilities.AndroidKVS
import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.Diagnostics
import com.amplitude.core.utilities.EventsFileManager
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.FileResponseHandler
import com.amplitude.core.utilities.JSONUtil
import com.amplitude.core.utilities.http.ResponseHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import java.io.File

class AndroidStorageV2(
    /**
     * A generic key to differentiate multiple storage instances.
     */
    storageKey: String,
    private val logger: Logger,
    /**
     * A place where the storage stores some metadata to manage this storage
     */
    val sharedPreferences: SharedPreferences,
    /**
     * A directory where the storage stores the actual data. This should not be shared with other
     * storage instances
     */
    storageDirectory: File,
    diagnostics: Diagnostics,
) : Storage, EventsFileStorage {
    private val eventsFile =
        EventsFileManager(
            storageDirectory,
            storageKey,
            AndroidKVS(sharedPreferences),
            logger,
            diagnostics
        )
    private val eventCallbacksMap = mutableMapOf<String, EventCallBack>()

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
        sharedPreferences.edit {
            putString(key.rawVal, value)
        }
    }

    override suspend fun remove(key: Storage.Constants) {
        sharedPreferences.edit {
            remove(key.rawVal)
        }
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

    override suspend fun getEventsString(filePath: Any): String {
        return eventsFile.getEventString(filePath as String)
    }

    override fun getResponseHandler(
        eventPipeline: EventPipeline,
        configuration: Configuration,
        scope: CoroutineScope,
        storageDispatcher: CoroutineDispatcher,
    ): ResponseHandler {
        return FileResponseHandler(
            this,
            eventPipeline,
            configuration,
            scope,
            storageDispatcher,
            logger,
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

    override fun splitEventFile(
        filePath: String,
        events: JSONArray,
    ) {
        eventsFile.splitFile(filePath, events)
    }

    fun cleanupMetadata() {
        eventsFile.cleanupMetadata()
    }
}

class AndroidEventsStorageProviderV2 : StorageProvider {
    override fun getStorage(
        amplitude: Amplitude,
        prefix: String?,
    ): Storage {
        val configuration = amplitude.configuration as com.amplitude.android.Configuration
        val sharedPreferencesName = "amplitude-events-${configuration.instanceName}"
        val sharedPreferences =
            configuration.context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        return AndroidStorageV2(
            configuration.instanceName,
            configuration.loggerProvider.getLogger(amplitude),
            sharedPreferences,
            AndroidStorageContextV3.getEventsStorageDirectory(configuration),
            amplitude.diagnostics,
        )
    }
}

class AndroidIdentifyInterceptStorageProviderV2 : StorageProvider {
    override fun getStorage(
        amplitude: Amplitude,
        prefix: String?,
    ): Storage {
        val configuration = amplitude.configuration as com.amplitude.android.Configuration
        val sharedPreferences = configuration.context.getSharedPreferences(
            "amplitude-identify-intercept-${configuration.instanceName}",
            Context.MODE_PRIVATE
        )
        return AndroidStorageV2(
            configuration.instanceName,
            configuration.loggerProvider.getLogger(amplitude),
            sharedPreferences,
            AndroidStorageContextV3.getIdentifyInterceptStorageDirectory(configuration),
            amplitude.diagnostics,
        )
    }
}
