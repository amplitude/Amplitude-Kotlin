package com.amplitude.android.utilities

import android.content.Context
import android.content.SharedPreferences
import com.amplitude.android.storage.AndroidStorageV2
import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.Storage
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.Diagnostics
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.http.ResponseHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray

class AndroidStorage(
    context: Context,
    val storageKey: String,
    logger: Logger,
    internal val prefix: String?,
    diagnostics: Diagnostics,
) : Storage, EventsFileStorage {
    companion object {
        const val STORAGE_PREFIX = "amplitude-android"
    }

    val sharedPreferences: SharedPreferences
    internal val storageV2: AndroidStorageV2

    init {
        val sharedPreferencesFile = "${getPrefix()}-$storageKey"
        sharedPreferences = context.getSharedPreferences(sharedPreferencesFile, Context.MODE_PRIVATE)
        val storageDirectory = context.getDir(getDir(), Context.MODE_PRIVATE)
        storageV2 =
            AndroidStorageV2(
                storageKey,
                logger,
                sharedPreferences,
                storageDirectory,
                diagnostics,
            )
    }

    override suspend fun writeEvent(event: BaseEvent) {
        storageV2.writeEvent(event)
    }

    override suspend fun write(
        key: Storage.Constants,
        value: String,
    ) {
        storageV2.write(key, value)
    }

    override suspend fun remove(key: Storage.Constants) {
        storageV2.remove(key)
    }

    override suspend fun rollover() {
        storageV2.rollover()
    }

    override fun read(key: Storage.Constants): String? {
        return storageV2.read(key)
    }

    override fun readEventsContent(): List<Any> {
        return storageV2.readEventsContent()
    }

    override fun releaseFile(filePath: String) {
        storageV2.releaseFile(filePath)
    }

    override suspend fun getEventsString(filePath: Any): String {
        return storageV2.getEventsString(filePath)
    }

    override fun getResponseHandler(
        eventPipeline: EventPipeline,
        configuration: Configuration,
        diagnosticsClient: DiagnosticsClient,
        scope: CoroutineScope,
        storageDispatcher: CoroutineDispatcher,
    ): ResponseHandler {
        return storageV2.getResponseHandler(eventPipeline, configuration, diagnosticsClient, scope, storageDispatcher)
    }

    override fun removeFile(filePath: String): Boolean {
        return storageV2.removeFile(filePath)
    }

    override fun getEventCallback(insertId: String): EventCallBack? {
        return storageV2.getEventCallback(insertId)
    }

    override fun removeEventCallback(insertId: String) {
        return storageV2.removeEventCallback(insertId)
    }

    override fun splitEventFile(
        filePath: String,
        events: JSONArray,
    ) {
        return storageV2.splitEventFile(filePath, events)
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
