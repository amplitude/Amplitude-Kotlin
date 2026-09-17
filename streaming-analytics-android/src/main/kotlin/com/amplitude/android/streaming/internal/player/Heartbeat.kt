package com.amplitude.android.streaming.internal.player

import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.android.streaming.internal.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

private const val HEARTBEAT_INTERVAL_MILLIS = 1_000L

internal class Heartbeat internal constructor(
    private val scope: CoroutineScope,
    private val stoppedEvent: suspend (timestamp: Long, isFinal: Boolean) -> Unit,
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
                    runCatchingCancellable {
                        sendHeartbeat()
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
        stopped.set(true)
        try {
            job?.cancelAndJoin()
        } finally {
            job = null
            withContext(NonCancellable) {
                sendMutex.withLock {
                    stoppedEvent(time.nowMillis(), true)
                }
            }
        }
    }

    private suspend fun sendHeartbeat() {
        sendMutex.withLock {
            if (stopped.get()) return
            stoppedEvent(time.nowMillis(), false)
        }
    }
}
