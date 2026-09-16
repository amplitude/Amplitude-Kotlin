package com.amplitude.android.streaming.internal

import androidx.media3.common.Player
import com.amplitude.android.Amplitude
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.internal.network.uploadPipeline
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
    private val graph = StreamingDiGraph(amplitude)

    init {
        graph.scope.launch {
            runCatchingCancellable {
                graph.uploadPipeline.onNewEvent()
            }.onFailure {
                graph.logger.error("startup upload drain error: ${it.localizedMessage}")
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun trackPlayer(
        player: Player,
        contentProvider: PlayerContentProvider,
    ) {
        // TODO: Not yet implemented
    }

    fun onDelayedEvent(event: DelayedEvent) {
        graph.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatchingCancellable {
                withContext(NonCancellable) {
                    graph.storagePipeline.onDelayedEvent(event)
                }
                graph.uploadPipeline.onNewEvent()
            }.onFailure {
                graph.logger.error("onDelayedEvent error: ${it.localizedMessage}")
            }
        }
    }

    fun flush() {
        graph.scope.launch {
            runCatchingCancellable {
                graph.uploadPipeline.flush()
            }.onFailure {
                graph.logger.error("flush error: ${it.localizedMessage}")
            }
        }
    }

    fun teardown() {
        graph.scope.cancel()
    }
}
