package com.amplitude.android.streaming.internal.player

import androidx.media3.common.MediaItem
import com.amplitude.android.streaming.internal.AdContext
import com.amplitude.android.streaming.internal.StopReason

internal sealed interface PlayerEvent {
    data object Playing : PlayerEvent

    data object Paused : PlayerEvent

    data object Buffering : PlayerEvent

    data object Ready : PlayerEvent

    data object Ended : PlayerEvent

    data class Seeking(
        val previousSnapshot: PlayerMediaSnapshot,
        val previousAdPositionMillis: Long? = null,
    ) : PlayerEvent

    data class Error(
        val message: String?,
    ) : PlayerEvent

    data class MediaChanged(
        val mediaItem: MediaItem?,
        val previousSnapshot: PlayerMediaSnapshot,
        val stopReason: StopReason,
    ) : PlayerEvent

    data class AdStarted(
        val ad: AdContext,
    ) : PlayerEvent

    data class AdStopped(
        val ad: AdContext,
        val completed: Boolean,
    ) : PlayerEvent

    data class AdSkipped(
        val ad: AdContext,
    ) : PlayerEvent
}
