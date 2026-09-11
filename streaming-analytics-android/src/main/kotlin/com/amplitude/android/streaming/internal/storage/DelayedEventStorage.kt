package com.amplitude.android.streaming.internal.storage

import android.content.Context
import com.amplitude.android.Configuration
import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.android.streaming.internal.util.lazySuspend
import com.amplitude.common.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

private const val STORAGE_DIR_NAME = "amplitude"
private const val DELAYED_EVENTS_PATH = "analytics/streaming-delayed-events"

internal val StreamingDiGraph.delayedEventStorage: DelayedEventStorage by weak {
    DelayedEventStorage(
        context = context,
        configuration = configuration,
        logger = logger,
        ioDispatcher = ioDispatcher,
    )
}

/**
 * Persists delayed-events request payloads as JSON files until they can be uploaded.
 *
 * Filesystem and parse failures are logged and contained so they do not reach the host app.
 */
internal class DelayedEventStorage(
    private val context: Context,
    private val configuration: Configuration,
    private val logger: Logger,
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
        runCatchingStorage("Failed to persist delayed-events queue entry $key") {
            val directory = directory()
            if (!directory.exists() && !directory.mkdirs()) {
                error("Failed to create delayed-events queue directory")
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
                    error("Failed to commit delayed-events queue entry")
                }
            } finally {
                temporary.delete()
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun read(key: String): DelayedEventsRequestEntity? =
        runCatchingStorage("Failed to read delayed-events queue entry $key") {
            File(directory(), "$key.json").inputStream().use { stream ->
                delayedEventsStorageJson.decodeFromStream<DelayedEventsRequestEntity>(stream)
            }
        }

    suspend fun findKey(idKey: String): String? =
        runCatchingStorage("Failed to look up delayed-events queue entry $idKey") {
            val matches =
                jsonFiles().filter { file ->
                    file.name.endsWith("-$idKey.json")
                }
            val oldest = matches.minByOrNull { it.name } ?: return@runCatchingStorage null
            for (extra in matches) {
                if (extra == oldest) continue
                if (!extra.delete()) {
                    logger.error(
                        "Failed to remove duplicate delayed-events queue entry ${extra.name}",
                    )
                }
            }
            oldest.nameWithoutExtension
        }

    suspend fun keys(): List<String> =
        runCatchingStorage("Failed to list delayed-events queue entries") {
            jsonFiles().map { it.nameWithoutExtension }
        } ?: emptyList()

    suspend fun delete(key: String) {
        runCatchingStorage("Failed to remove delayed-events queue entry $key") {
            val file = File(directory(), "$key.json")
            if (file.exists() && !file.delete()) {
                error("Failed to remove delayed-events queue entry ${file.name}")
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

    private suspend fun <T> runCatchingStorage(
        message: String,
        block: suspend () -> T,
    ): T? =
        withContext(ioDispatcher) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.error("$message: ${error.message}")
                null
            }
        }
}

internal val delayedEventsStorageJson =
    Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true
    }
