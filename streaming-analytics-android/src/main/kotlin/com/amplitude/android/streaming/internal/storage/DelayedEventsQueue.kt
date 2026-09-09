package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.singleton
import com.amplitude.common.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal val StreamingDiGraph.delayedEventsQueue: DelayedEventsQueue by singleton {
    DelayedEventsQueue(
        storage = delayedEventStorage,
        logger = logger,
        storageKey = "${context.packageName}/${configuration.instanceName}",
    )
}

/**
 * Ordered delayed-events queue that merges updates for the same stream session.
 */
internal class DelayedEventsQueue(
    private val storage: DelayedEventStorage,
    private val logger: Logger,
    storageKey: String,
) {
    private val mutex = mutexes.getOrPut(storageKey) { Mutex() }

    suspend fun enqueue(request: DelayedEventsRequestEntity) {
        mutex.withLock {
            val idKey = request.id.storageKey()
            val existingKey = storage.findKey(idKey)
            val key = existingKey ?: "${nextSequence().toString().padStart(19, '0')}-$idKey"
            val stored =
                if (existingKey != null) {
                    try {
                        val previous = storage.read(existingKey)
                        previous.mergedWith(request)
                    } catch (error: SerializationException) {
                        logger.error("Replacing corrupt delayed-events queue entry $existingKey: ${error.message}")
                        request
                    }
                } else {
                    request
                }
            storage.write(key, stored)
        }
    }

    suspend fun peek(skipIds: Set<String>): DelayedEventsRequestEntity? {
        return mutex.withLock {
            for (key in storage.keys().sorted()) {
                val request =
                    try {
                        storage.read(key)
                    } catch (error: SerializationException) {
                        logger.error("Dropping corrupt delayed-events queue entry $key: ${error.message}")
                        storage.delete(key)
                        continue
                    }
                if (request.id in skipIds) continue
                return@withLock request.copy(queueKey = key)
            }
            null
        }
    }

    suspend fun removeIfUnchanged(request: DelayedEventsRequestEntity) {
        mutex.withLock {
            val key = request.queueKey ?: return@withLock
            try {
                val stored = storage.read(key)
                if (stored == request.copy(queueKey = null)) {
                    storage.delete(key)
                }
            } catch (_: FileNotFoundException) {
                // Already removed.
            } catch (_: SerializationException) {
                // Leave changed or corrupt entries for peek to handle.
            }
        }
    }

    /**
     * Read the on-disk max under [mutex] so two graphs sharing [storageKey] cannot mint the same id.
     */
    private suspend fun nextSequence(): Long {
        val previous =
            storage.keys()
                .maxOfOrNull {
                    it.substringBefore('-')
                        .toLongOrNull()
                        ?: 0L
                } ?: 0L
        return maxOf(previous + 1, System.currentTimeMillis())
    }

    private companion object {
        val mutexes = ConcurrentHashMap<String, Mutex>()
    }
}

private fun DelayedEventsRequestEntity.mergedWith(
    incoming: DelayedEventsRequestEntity,
): DelayedEventsRequestEntity {
    if (incoming.events.isEmpty()) {
        return incoming.copy(
            events = events,
            timeoutMillis = timeoutMillis,
            instantEvents = mergedInstantEvents(incoming.instantEvents),
        )
    }
    if (incoming.eventTime() <= eventTime()) {
        return copy(instantEvents = mergedInstantEvents(incoming.instantEvents))
    }
    val nextIds = incoming.events.mapNotNull { it.insertId() }.toSet()
    val promoted =
        if (nextIds.isEmpty()) {
            emptyList()
        } else {
            events.filter { it.insertId() !in nextIds }
        }
    return incoming.copy(
        instantEvents = mergedInstantEvents(promoted + incoming.instantEvents.orEmpty()),
    )
}

private fun DelayedEventsRequestEntity.mergedInstantEvents(
    incoming: List<DelayedEventEntity>?,
): List<DelayedEventEntity>? =
    (instantEvents.orEmpty() + incoming.orEmpty())
        .distinct()
        .takeIf { it.isNotEmpty() }

private fun DelayedEventsRequestEntity.eventTime(): Long =
    events.maxOfOrNull { it.timeMillis() } ?: Long.MIN_VALUE

private fun DelayedEventEntity.timeMillis(): Long =
    (ingestJson["time"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: Long.MIN_VALUE

private fun DelayedEventEntity.insertId(): String? =
    (ingestJson["insert_id"] as? JsonPrimitive)?.contentOrNull

private fun String.storageKey(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte ->
            (byte.toInt() and 0xff)
                .toString(16)
                .padStart(2, '0')
        }
