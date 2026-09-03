package com.amplitude.android.streaming.internal.storage

import android.content.Context
import com.amplitude.android.Configuration
import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.android.streaming.internal.util.lazySuspend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

private const val STORAGE_DIR_NAME = "amplitude"
private const val DELAYED_EVENTS_PATH = "analytics/streaming-delayed-events"

internal val StreamingDiGraph.delayedEventStorage: DelayedEventStorage by weak {
    DelayedEventStorage(
        context = context,
        configuration = configuration,
        ioDispatcher = ioDispatcher,
    )
}

internal class DelayedEventStorage(
    private val context: Context,
    private val configuration: Configuration,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val directory =
        lazySuspend {
            withContext(ioDispatcher) {
                File(
                    context.getDir(STORAGE_DIR_NAME, Context.MODE_PRIVATE),
                    "${context.packageName}/${configuration.instanceName}/$DELAYED_EVENTS_PATH",
                )
            }
        }

    suspend fun write(
        key: String,
        request: DelayedEventsRequestEntity,
    ) {
        withContext(ioDispatcher) {
            val directory = directory()
            check(directory.exists() || directory.mkdirs()) {
                "Failed to create delayed-events queue directory"
            }
            val destination = File(directory, "$key.json")
            val temporary = File(directory, "$key-${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    val byteArray =
                        delayedEventsStorageJson.encodeToString(request).toByteArray(Charsets.UTF_8)
                    output.write(byteArray)
                    output.fd.sync()
                }
                if (!temporary.renameTo(destination)) {
                    throw IOException("Failed to commit delayed-events queue entry")
                }
            } finally {
                temporary.delete()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun read(key: String): DelayedEventsRequestEntity =
        withContext(ioDispatcher) {
            File(directory(), "$key.json").inputStream().use { stream ->
                delayedEventsStorageJson.decodeFromStream<DelayedEventsRequestEntity>(stream)
            }
        }

    suspend fun findKey(idKey: String): String? =
        withContext(ioDispatcher) {
            val matches =
                jsonFiles().filter { file ->
                    file.name.endsWith("-$idKey.json")
                }
            val oldest = matches.minByOrNull { it.name } ?: return@withContext null
            for (extra in matches) {
                if (extra == oldest) continue
                if (!extra.delete()) {
                    throw IOException("Failed to remove duplicate delayed-events queue entry ${extra.name}")
                }
            }
            oldest.nameWithoutExtension
        }

    suspend fun keys(): List<String> =
        withContext(ioDispatcher) {
            jsonFiles().map { it.nameWithoutExtension }
        }

    suspend fun delete(key: String) {
        withContext(ioDispatcher) {
            val file = File(directory(), "$key.json")
            if (file.exists() && !file.delete()) {
                throw IOException("Failed to remove delayed-events queue entry ${file.name}")
            }
        }
    }

    private suspend fun jsonFiles(): List<File> =
        directory()
            .listFiles { candidate ->
                candidate.extension == "json"
            }
            .orEmpty()
            .toList()
}

internal val delayedEventsStorageJson =
    Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true
    }
