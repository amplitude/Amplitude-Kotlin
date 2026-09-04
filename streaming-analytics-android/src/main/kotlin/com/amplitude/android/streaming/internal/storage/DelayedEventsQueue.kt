package com.amplitude.android.streaming.internal.storage

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.singleton
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.android.streaming.internal.util.lazySuspend
import com.amplitude.common.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

internal val StreamingDiGraph.delayedEventsQueue: DelayedEventsQueue by singleton {
    DelayedEventsQueue(
        storage = delayedEventStorage,
        logger = logger,
    )
}

internal class DelayedEventsQueue(
    private val storage: DelayedEventStorage,
    private val logger: Logger,
) {
    private val mutex = Mutex()
    private val sequence =
        lazySuspend {
            val initialValue = storage.keys()
                .maxOfOrNull {
                    it.substringBefore('-')
                        .toLongOrNull()
                        ?: 0L
                } ?: 0L
            AtomicLong(initialValue)
        }

    suspend fun enqueue(request: DelayedEventsRequestEntity) {
        mutex.withLock {
            val idKey = request.id.storageKey()
            val existingKey = storage.findKey(idKey)
            val key = existingKey ?: "${nextSequence().toString().padStart(19, '0')}-$idKey"
            val stored =
                if (existingKey != null) {
                    try {
                        val previous = storage.read(existingKey)
                        request.copy(
                            events = request.events.ifEmpty { previous.events },
                            timeoutMillis =
                                if (request.events.isEmpty()) {
                                    previous.timeoutMillis
                                } else {
                                    request.timeoutMillis
                                },
                            instantEvents =
                                (previous.instantEvents.orEmpty() + request.instantEvents.orEmpty())
                                    .distinct()
                                    .takeIf { it.isNotEmpty() },
                        )
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

    suspend fun remove(request: DelayedEventsRequestEntity) {
        mutex.withLock {
            request.queueKey?.let { storage.delete(it) }
        }
    }

    /**
     * True when [request] still matches the on-disk payload for its [DelayedEventsRequestEntity.queueKey].
     * Missing files count as a match so a later [remove] is a no-op.
     */
    suspend fun matches(request: DelayedEventsRequestEntity): Boolean =
        mutex.withLock {
            val key = request.queueKey ?: return@withLock true
            try {
                storage.read(key).copy(queueKey = null) == request.copy(queueKey = null)
            } catch (_: FileNotFoundException) {
                true
            } catch (_: SerializationException) {
                false
            }
        }

    /**
     * Compare-and-set loop instead of [AtomicLong.updateAndGet], which needs API 24.
     */
    private suspend fun nextSequence(): Long {
        val sequence = sequence()
        while (true) {
            val previous = sequence.get()
            val next = maxOf(previous + 1, System.currentTimeMillis())
            if (sequence.compareAndSet(previous, next)) {
                return next
            }
        }
    }
}

private fun String.storageKey(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte ->
            (byte.toInt() and 0xff)
                .toString(16)
                .padStart(2, '0')
        }
