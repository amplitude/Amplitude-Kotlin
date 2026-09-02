package com.amplitude.android.streaming.internal

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import java.util.IdentityHashMap

/**
 * Per-Amplitude Streaming Analytics state.
 */
internal class StreamingAnalytics private constructor(
    private val amplitude: Amplitude,
) {
    @OptIn(AmplitudePreview::class)
    @Suppress("UNUSED_PARAMETER")
    fun trackPlayer(
        player: Player,
        contentProvider: PlayerContentProvider,
    ) {
        // TODO: Not yet implemented
    }

    fun teardown() {
        synchronized(lock) {
            if (instances[amplitude] === this) {
                instances.remove(amplitude)
            }
        }
        // TODO: Not yet implemented
    }

    companion object {
        private val lock = Any()
        private val instances = IdentityHashMap<Amplitude, StreamingAnalytics>()

        fun from(amplitude: Amplitude): StreamingAnalytics {
            synchronized(lock) {
                return instances.getOrPut(amplitude) {
                    StreamingAnalytics(amplitude)
                }
            }
        }

        fun teardown(amplitude: Amplitude) {
            synchronized(lock) {
                instances[amplitude]
            }?.teardown()
        }
    }
}
