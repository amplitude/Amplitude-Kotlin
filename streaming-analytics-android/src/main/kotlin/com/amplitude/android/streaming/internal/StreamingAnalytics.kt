package com.amplitude.android.streaming.internal

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview

/**
 * Per-Amplitude Streaming Analytics state.
 */
@OptIn(AmplitudePreview::class)
@Suppress("UNUSED_PARAMETER")
internal class StreamingAnalytics(
    private val amplitude: Amplitude,
) {
    fun trackPlayer(
        player: Player,
        contentProvider: PlayerContentProvider,
    ) {
        // TODO: Not yet implemented
    }

    fun queueDelayedEvent(event: DelayedEvent) {
        // TODO: Not yet implemented
    }

    fun teardown() {
        // TODO: Not yet implemented
    }
}
