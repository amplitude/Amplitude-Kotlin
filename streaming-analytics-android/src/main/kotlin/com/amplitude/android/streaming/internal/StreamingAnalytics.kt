package com.amplitude.android.streaming.internal

import androidx.media3.common.Player
import com.amplitude.android.Amplitude
import com.amplitude.android.streaming.internal.network.uploadPipeline
import com.amplitude.android.streaming.internal.player.playerBindingFactory
import com.amplitude.android.streaming.internal.storage.storagePipeline
import com.amplitude.android.streaming.internal.util.runCatchingCancellable
import com.amplitude.core.AmplitudePreview
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Per-Amplitude Streaming Analytics state.
 */
@OptIn(AmplitudePreview::class)
internal class StreamingAnalytics(
    amplitude: Amplitude,
) {
    private var graph: StreamingDiGraph? = StreamingDiGraph(amplitude)

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

    fun onDelayedEvent(event: DelayedEvent) {
        launchSafeAsync("onDelayedEvent error", CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                storagePipeline.onDelayedEvent(event)
            }
            uploadPipeline.onNewEvent()
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
     */
    fun teardown() {
        val graph = this.graph ?: return
        this.graph = null
        val terminalEvents = ConcurrentLinkedQueue<DelayedEvent>()
        graph.streamTracker.routeDelayedEvents { terminalEvents.add(it) }
        graph.amplitude.amplitudeScope.launch(graph.ioDispatcher) {
            runCatchingCancellable {
                withContext(NonCancellable) {
                    // Closes the factory, so an in-flight trackPlayer that is still waiting on
                    // its lock cannot open a session after this drain.
                    graph.playerBindingFactory.detachAll()
                    terminalEvents.forEach { graph.storagePipeline.onDelayedEvent(it) }
                    graph.uploadPipeline.flush()
                }
            }.onFailure {
                graph.logger.error("teardown error: ${it.localizedMessage}")
            }
            graph.scope.cancel()
        }
    }

    private suspend inline fun runSafe(
        errorMsg: String,
        crossinline block: suspend StreamingDiGraph.() -> Unit,
    ) {
        graph?.run {
            runCatchingCancellable {
                block()
            }.onFailure {
                logger.error("$errorMsg: ${it.localizedMessage}")
            }
        }
    }

    private inline fun launchSafeAsync(
        errorMsg: String,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        crossinline block: suspend StreamingDiGraph.() -> Unit,
    ) {
        graph?.apply {
            scope.launch(start = start) {
                runCatchingCancellable {
                    block()
                }.onFailure {
                    logger.error("$errorMsg: ${it.localizedMessage}")
                }
            }
        }
    }
}
