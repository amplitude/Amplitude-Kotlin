package com.amplitude.android.streaming.internal.player

import com.amplitude.android.streaming.internal.util.Time
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

private const val HEARTBEAT_INTERVAL_MILLIS = 1_000L

internal class Heartbeat internal constructor(
    private val scope: CoroutineScope,
    private val stoppedEvent: suspend (timestamp: Long) -> Unit,
    private val time: Time,
) {
    private val sendMutex = Mutex()
    private var job: Job? = null
    private val stopped = AtomicBoolean(false)

    fun start() {
        if (job != null || stopped.get()) return
        job =
            scope.launch {
                while (true) {
                    try {
                        sendHeartbeat()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Keep upserting; a single snapshot/track failure must not end the play.
                    }
                    delay(HEARTBEAT_INTERVAL_MILLIS.milliseconds)
                }
            }
    }

    suspend fun cancel() {
        if (!stopped.compareAndSet(false, true)) return
        job?.cancelAndJoin()
        job = null
    }

    suspend fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        job?.cancelAndJoin()
        job = null
        sendMutex.withLock {
            stoppedEvent(time.nowMillis())
        }
    }

    private suspend fun sendHeartbeat() {
        sendMutex.withLock {
            if (stopped.get()) return
            stoppedEvent(time.nowMillis())
        }
    }
}
