package com.amplitude.android.streaming.internal

import androidx.media3.common.Player
import com.amplitude.android.Amplitude
import com.amplitude.android.streaming.internal.network.uploadPipeline
import com.amplitude.android.streaming.internal.player.playerBindingFactory
import com.amplitude.android.streaming.internal.storage.storagePipeline
import com.amplitude.android.streaming.internal.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentLinkedQueue

private const val TEARDOWN_TIMEOUT_MILLIS = 10_000L

/**
 * Per-Amplitude Streaming Analytics state.
 */
internal class StreamingAnalytics(
    private val graph: StreamingDiGraph,
) {
    constructor(amplitude: Amplitude) : this(StreamingDiGraph(amplitude))

    @Volatile
    private var closed = false
    private val persistenceJob = SupervisorJob(graph.scope.coroutineContext[Job])

    init {
        launchSafeAsync("startup upload drain error") {
            uploadPipeline.onNewEvent()
        }
    }

    suspend fun trackPlayer(player: Player) {
        runSafe("trackPlayer error") {
            playerBindingFactory.getOrCreate(player)
        }
    }

    suspend fun untrackPlayer(player: Player) {
        runSafe("untrackPlayer error") {
            playerBindingFactory.detach(player)
        }
    }

    @Synchronized
    fun onDelayedEvent(event: DelayedEvent) {
        if (closed) return
        // Register the child before teardown can close intake and join accepted writes.
        graph.scope.launch(persistenceJob, start = CoroutineStart.UNDISPATCHED) {
            runCatchingCancellable {
                val events = graph.delayedEventBatcher.collect(event) ?: return@runCatchingCancellable
                graph.storagePipeline.onDelayedEvents(events)
                graph.uploadPipeline.onNewEvent()
            }.onFailure {
                graph.logger.error("onDelayedEvent error: ${it.localizedMessage}")
            }
        }
    }

    fun flush() {
        launchSafeAsync("flush error") {
            uploadPipeline.flush()
        }
    }

    /**
     * Stops every tracked player and drains their terminal events.
     *
     * Asynchronous: the final stop reads the player on its own looper, which is the caller's
     * thread when an app removes the plugin from the main thread.
     * Allows ten seconds for pending writes and uploads before cancelling the streaming scope.
     */
    @Synchronized
    fun teardown() {
        if (closed) return
        closed = true
        persistenceJob.complete()
        val terminalEvents = ConcurrentLinkedQueue<DelayedEvent>()
        graph.streamTracker.routeDelayedEvents { terminalEvents.add(it) }
        graph.amplitude.amplitudeScope.launch(graph.ioDispatcher) {
            try {
                runCatchingCancellable {
                    withContext(NonCancellable) {
                        val drained = withTimeoutOrNull(TEARDOWN_TIMEOUT_MILLIS) {
                            // Close the factory before waiting for accepted writes, so no new
                            // session can open and no older heartbeat can follow a final stop.
                            graph.playerBindingFactory.detachAll()
                            persistenceJob.join()
                            terminalEvents.mapNotNull { event ->
                                graph.delayedEventBatcher.collect(event)
                            }.forEach { events ->
                                graph.storagePipeline.onDelayedEvents(events)
                            }
                            graph.uploadPipeline.flushAndDrain()
                            true
                        }
                        if (drained == null) graph.logger.error("Streaming teardown timed out; cancelling pending work")
                    }
                }.onFailure {
                    graph.logger.error("teardown error: ${it.localizedMessage}")
                }
            } finally {
                graph.scope.cancel()
            }
        }
    }

    private suspend inline fun runSafe(
        errorMsg: String,
        crossinline block: suspend StreamingDiGraph.() -> Unit,
    ) {
        if (closed) return
        graph.run {
            runCatchingCancellable {
                block()
            }.onFailure {
                logger.error("$errorMsg: ${it.localizedMessage}")
            }
        }
    }

    private inline fun launchSafeAsync(
        errorMsg: String,
        crossinline block: suspend StreamingDiGraph.() -> Unit,
    ) {
        if (closed) return
        graph.apply {
            scope.launch {
                runCatchingCancellable {
                    block()
                }.onFailure {
                    logger.error("$errorMsg: ${it.localizedMessage}")
                }
            }
        }
    }
}
