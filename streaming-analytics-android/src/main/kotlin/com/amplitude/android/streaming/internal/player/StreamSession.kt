package com.amplitude.android.streaming.internal.player

import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.MediaType
import com.amplitude.android.streaming.internal.StopReason
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.core.AmplitudePreview

@OptIn(AmplitudePreview::class)
internal class StreamSession(
    val streamSessionId: String,
    val startedInsertId: String,
    val options: PlayerContent,
    val mediaType: MediaType,
    private val time: Time,
) {
    var stopReason: StopReason? = null
    var errorMessage: String? = null

    private var watchDurationMillis = 0L
    private var watchStartedAt: Long? = null

    fun resumeWatch() {
        if (watchStartedAt == null) {
            watchStartedAt = time.elapsedRealtime()
        }
    }

    fun pauseWatch() {
        val startedAt = watchStartedAt ?: return
        watchDurationMillis += (time.elapsedRealtime() - startedAt).coerceAtLeast(0)
        watchStartedAt = null
    }

    fun durationMillis(): Long = watchDurationMillis + currentWatchSegment()

    private fun currentWatchSegment(): Long =
        watchStartedAt?.let { (time.elapsedRealtime() - it).coerceAtLeast(0) } ?: 0
}
