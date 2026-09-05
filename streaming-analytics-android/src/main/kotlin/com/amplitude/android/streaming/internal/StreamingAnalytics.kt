package com.amplitude.android.streaming.internal

import androidx.media3.common.Player
import com.amplitude.android.Amplitude
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.internal.network.uploadPipeline
import com.amplitude.android.streaming.internal.player.playerBindingFactory
import com.amplitude.android.streaming.internal.storage.storagePipeline
import com.amplitude.core.AmplitudePreview
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

    fun trackPlayer(
        player: Player,
        contentProvider: PlayerContentProvider,
    ) {
        graph?.apply {
            playerBindingFactory.getOrCreate(
                player = player,
                contentProvider = contentProvider,
            )
        }
    }

    fun onDelayedEvent(event: DelayedEvent) {
        graph?.apply {
            scope.launch {
                withContext(NonCancellable) {
                    storagePipeline.onDelayedEvent(event)
                }
                uploadPipeline.onNewEvent()
            }
        }
    }

    fun flush() {
        graph?.apply {
            scope.launch {
                uploadPipeline.flush()
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
