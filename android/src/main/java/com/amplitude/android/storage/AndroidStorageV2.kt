package com.amplitude.android.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.amplitude.android.utilities.AndroidKVS
import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.EventCallBack
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.diagnostics.DiagnosticsClientProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.Diagnostics
import com.amplitude.core.utilities.EventsFileManager
import com.amplitude.core.utilities.EventsFileStorage
import com.amplitude.core.utilities.FileResponseHandler
import com.amplitude.core.utilities.JSONUtil
import com.amplitude.core.utilities.http.AnalyticsResponse
import com.amplitude.core.utilities.http.ResponseHandler
import com.amplitude.core.utilities.http.SuccessResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import java.io.File

private const val UPLOAD_MARKER_PREFIX = "upload_attempt_sample."

@OptIn(RestrictedAmplitudeFeature::class)
@Deprecated("Not intended for public use. Will be internal in a future release.")
public class AndroidStorageV2
    internal constructor(
        /**
         * A generic key to differentiate multiple storage instances.
         */
        storageKey: String,
        private val logger: Logger,
        /**
         * A place where the storage stores some metadata to manage this storage
         */
        public val sharedPreferences: SharedPreferences,
        /**
         * A directory where the storage stores the actual data. This should not be shared with other
         * storage instances
         */
        storageDirectory: File,
        diagnostics: Diagnostics,
        private val diagnosticsClientProvider: DiagnosticsClientProvider? = null,
        private val sampleUploadAttempts: Boolean = false,
    ) : Storage, EventsFileStorage {
        public constructor(
            storageKey: String,
            logger: Logger,
            sharedPreferences: SharedPreferences,
            storageDirectory: File,
            diagnostics: Diagnostics,
        ) : this(storageKey, logger, sharedPreferences, storageDirectory, diagnostics, null)

        private val eventsFile =
            EventsFileManager(
                storageDirectory,
                storageKey,
                AndroidKVS(sharedPreferences),
                logger,
                diagnostics,
            )
        private val eventCallbacksMap = mutableMapOf<String, EventCallBack>()

        private var uploadCountersRecovered = false

        // Recover once, on the first queue scan after initialization (off the UI thread).
        // This sample assumes one initialization per instance name per process.
        @Synchronized
        private fun recoverUploadCountersIfNeeded() {
            if (uploadCountersRecovered) return
            uploadCountersRecovered = true
            if (!sampleUploadAttempts) return

            try {
                for (phase in listOf("network_callback", "cleanup")) {
                    val key = UPLOAD_MARKER_PREFIX + phase
                    val count = sharedPreferences.getStringSet(key, emptySet())?.size ?: 0
                    if (count > 0) {
                        diagnosticsClientProvider?.get()?.increment("analytics.upload.missed_$phase", count.toLong())
                    }
                    persistUploadMarkers(sharedPreferences.edit().remove(key))
                }
            } catch (e: Exception) {
                logger.warn("Could not recover upload diagnostic counters: ${e.javaClass.simpleName}")
            }
        }

        @Synchronized
        private fun markUpload(filePath: String, phase: String?) {
            if (!sampleUploadAttempts) return
            try {
                val batch = File(filePath).name
                val editor = sharedPreferences.edit()
                for (pendingPhase in listOf("network_callback", "cleanup")) {
                    val key = UPLOAD_MARKER_PREFIX + pendingPhase
                    val batches = sharedPreferences.getStringSet(key, emptySet()).orEmpty().toMutableSet()
                    if (phase == pendingPhase) batches.add(batch) else batches.remove(batch)
                    if (batches.isEmpty()) editor.remove(key) else editor.putStringSet(key, batches)
                }
                persistUploadMarkers(editor)
            } catch (e: Exception) {
                logger.warn("Could not persist upload diagnostic marker: ${e.javaClass.simpleName}")
            }
        }

        private fun persistUploadMarkers(editor: SharedPreferences.Editor) {
            // Synchronous persistence is intentional for this local crash-diagnostic sample.
            if (!editor.commit()) {
                logger.warn("Could not persist upload diagnostic marker")
            }
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
            recoverUploadCountersIfNeeded()
            return eventsFile.read()
        }

        override fun releaseFile(filePath: String) {
            eventsFile.release(filePath)
        }

        override suspend fun getEventsString(filePath: Any): String {
            return eventsFile.getEventString(filePath as String).also {
                if (it.isNotEmpty()) markUpload(filePath, "network_callback")
            }
        }

        override fun getResponseHandler(
            eventPipeline: EventPipeline,
            configuration: Configuration,
            scope: CoroutineScope,
            storageDispatcher: CoroutineDispatcher,
        ): ResponseHandler {
            val delegate =
                FileResponseHandler(
                    this,
                    eventPipeline,
                    configuration,
                    scope,
                    storageDispatcher,
                    logger,
                    diagnosticsClientProvider?.get(),
                )
            return object : ResponseHandler by delegate {
                override fun handle(
                    response: AnalyticsResponse,
                    events: Any,
                    eventsString: String,
                ): Boolean? {
                    val path = events as String
                    markUpload(path, if (response is SuccessResponse) "cleanup" else null)
                    return delegate.handle(response, events, eventsString)
                }
            }
        }

        override fun removeFile(filePath: String): Boolean {
            return eventsFile.remove(filePath).also { removed ->
                if (removed) markUpload(filePath, null)
            }
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

        public fun cleanupMetadata() {
            eventsFile.cleanupMetadata()
        }
    }

@OptIn(RestrictedAmplitudeFeature::class)
@Deprecated("Not intended for public use. Will be internal in a future release.")
public class AndroidEventsStorageProviderV2 : StorageProvider {
    override fun getStorage(
        amplitude: Amplitude,
        prefix: String?,
    ): Storage {
        val configuration = amplitude.configuration as com.amplitude.android.Configuration
        val sharedPreferencesName = "amplitude-events-${configuration.instanceName}"
        val sharedPreferences =
            configuration.context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        return AndroidStorageV2(
            storageKey = configuration.instanceName,
            logger = configuration.loggerProvider.getLogger(amplitude),
            sharedPreferences = sharedPreferences,
            storageDirectory = AndroidStorageContextV3.getEventsStorageDirectory(configuration),
            diagnostics = amplitude.diagnostics,
            diagnosticsClientProvider = DiagnosticsClientProvider { amplitude.diagnosticsClient },
            sampleUploadAttempts = configuration.enableDiagnostics,
        )
    }
}

@OptIn(RestrictedAmplitudeFeature::class)
@Deprecated("Not intended for public use. Will be internal in a future release.")
public class AndroidIdentifyInterceptStorageProviderV2 : StorageProvider {
    override fun getStorage(
        amplitude: Amplitude,
        prefix: String?,
    ): Storage {
        val configuration = amplitude.configuration as com.amplitude.android.Configuration
        val sharedPreferences =
            configuration.context.getSharedPreferences(
                "amplitude-identify-intercept-${configuration.instanceName}",
                Context.MODE_PRIVATE,
            )
        return AndroidStorageV2(
            configuration.instanceName,
            configuration.loggerProvider.getLogger(amplitude),
            sharedPreferences,
            AndroidStorageContextV3.getIdentifyInterceptStorageDirectory(configuration),
            amplitude.diagnostics,
            DiagnosticsClientProvider { amplitude.diagnosticsClient },
        )
    }
}
