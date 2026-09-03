package com.amplitude.android.streaming.internal

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import java.lang.ref.WeakReference

/**
 * Per-Amplitude Streaming Analytics state.
 */
internal class StreamingAnalytics private constructor(
    amplitude: Amplitude,
) {
    private val amplitudeRef = WeakReference(amplitude)
    @OptIn(AmplitudePreview::class)
    @Suppress("UNUSED_PARAMETER")
    fun trackPlayer(
        player: Player,
        contentProvider: PlayerContentProvider,
    ) {
        // TODO: Not yet implemented
    }

    fun teardown() {
        val amplitude = amplitudeRef.get() ?: return
        synchronized(lock) {
            findEntry(amplitude)?.let { (key, value) ->
                if (value === this) {
                    instances.remove(key)
                }
            }
        }
        // TODO: Not yet implemented
    }

    companion object {
        private val lock = Any()
        private val instances = mutableMapOf<WeakReference<Amplitude>, StreamingAnalytics>()

        private fun pruneStaleEntries() {
            instances.entries.removeIf { it.key.get() == null }
        }

        private fun findEntry(
            amplitude: Amplitude,
        ): Map.Entry<WeakReference<Amplitude>, StreamingAnalytics>? =
            instances.entries.firstOrNull { it.key.get() === amplitude }

        fun from(amplitude: Amplitude): StreamingAnalytics {
            synchronized(lock) {
                pruneStaleEntries()
                return findEntry(amplitude)?.value ?: StreamingAnalytics(amplitude).also {
                    instances[WeakReference(amplitude)] = it
                }
            }
        }

        fun teardown(amplitude: Amplitude) {
            synchronized(lock) {
                pruneStaleEntries()
                findEntry(amplitude)?.value?.teardown()
            }
        }
    }
}
