package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.singleton
import com.amplitude.common.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
                    val previous = storage.read(existingKey)
                    if (previous == null) {
                        logger.error("Replacing unreadable delayed-events queue entry $existingKey")
                        request
                    } else {
                        previous.mergedWith(request)
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
                val request = storage.read(key)
                if (request == null) {
                    logger.error("Dropping unreadable delayed-events queue entry $key")
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
            val stored = storage.read(key) ?: return@withLock
            if (stored == request.copy(queueKey = null)) {
                storage.delete(key)
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
            instantEvents =
                (instantEvents.orEmpty() + incoming.instantEvents.orEmpty())
                    .distinct()
                    .takeIf { it.isNotEmpty() },
        )
    }
    val nextIds = incoming.events.mapNotNull { it.insertId() }.toSet()
    val promoted =
        if (nextIds.isEmpty()) {
            emptyList()
        } else {
            events.filter { it.insertId() !in nextIds }
        }
    return incoming.copy(
        instantEvents =
            (instantEvents.orEmpty() + promoted + incoming.instantEvents.orEmpty())
                .distinct()
                .takeIf { it.isNotEmpty() },
    )
}

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
