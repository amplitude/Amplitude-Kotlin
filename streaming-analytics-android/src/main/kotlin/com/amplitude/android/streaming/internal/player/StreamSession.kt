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
    val stoppedInsertId: String,
    val options: PlayerContent,
    val mediaType: MediaType,
    snapshot: PlayerMediaSnapshot,
    private val time: Time,
) {
    @Volatile
    var snapshot: PlayerMediaSnapshot = snapshot
        private set

    @Volatile
    var stopReason: StopReason? = null

    @Volatile
    var errorMessage: String? = null

    @Volatile
    var frozen: Boolean = false
        private set

    private var frozenDurationMillis = 0L
    private var watchDurationMillis = 0L
    private var watchStartedAt: Long? = null

    @Synchronized
    fun resumeWatch() {
        if (frozen) return
        if (watchStartedAt == null) {
            watchStartedAt = time.elapsedRealtime()
        }
    }

    @Synchronized
    fun pauseWatch() {
        val startedAt = watchStartedAt ?: return
        watchDurationMillis += (time.elapsedRealtime() - startedAt).coerceAtLeast(0)
        watchStartedAt = null
    }

    @Synchronized
    fun durationMillis(): Long {
        if (frozen) return frozenDurationMillis
        return watchDurationMillis + currentWatchSegment()
    }

    @Synchronized
    fun updateSnapshot(snapshot: PlayerMediaSnapshot) {
        if (frozen) return
        this.snapshot = snapshot
    }

    @Synchronized
    fun freeze(snapshot: PlayerMediaSnapshot) {
        if (frozen) return
        pauseWatch()
        this.snapshot = snapshot
        frozenDurationMillis = durationMillis()
        frozen = true
    }

    private fun currentWatchSegment(): Long =
        watchStartedAt?.let { (time.elapsedRealtime() - it).coerceAtLeast(0) } ?: 0
}
