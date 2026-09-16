package com.amplitude.android.streaming.internal

import androidx.media3.common.Player
import com.amplitude.android.Amplitude
import com.amplitude.android.streaming.PlayerContentProvider
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

/**
 * Per-Amplitude Streaming Analytics state.
 */
@OptIn(AmplitudePreview::class)
internal class StreamingAnalytics(
    amplitude: Amplitude,
) {
    private var graph: StreamingDiGraph? = StreamingDiGraph(amplitude)

    init {
        graph?.let { graph ->
            graph.scope.launch {
                runCatchingCancellable {
                    graph.uploadPipeline.onNewEvent()
                }.onFailure {
                    graph.logger.error("startup upload drain error: ${it.localizedMessage}")
                }
            }
        }
    }

    fun trackPlayer(
        player: Player,
        contentProvider: PlayerContentProvider,
    ) {
        graph?.apply {
            runCatchingCancellable {
                playerBindingFactory.getOrCreate(
                    player = player,
                    contentProvider = contentProvider,
                )
            }.onFailure {
                logger.error("trackPlayer error: ${it.localizedMessage}")
            }
        }
    }

    fun onDelayedEvent(event: DelayedEvent) {
        graph?.apply {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                runCatchingCancellable {
                    withContext(NonCancellable) {
                        storagePipeline.onDelayedEvent(event)
                    }
                    uploadPipeline.onNewEvent()
                }.onFailure {
                    logger.error("onDelayedEvent error: ${it.localizedMessage}")
                }
            }
        }
    }

    fun flush() {
        graph?.apply {
            scope.launch {
                runCatchingCancellable {
                    uploadPipeline.flush()
                }.onFailure {
                    logger.error("flush error: ${it.localizedMessage}")
                }
            }
        }
    }

    fun teardown() {
        graph?.apply {
            playerBindingFactory.detachAll()
            scope.cancel()
        }
        graph = null
    }
}
