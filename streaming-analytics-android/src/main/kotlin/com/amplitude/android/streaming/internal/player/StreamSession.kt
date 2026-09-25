package com.amplitude.android.streaming.internal.player

import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.MediaType
import com.amplitude.android.streaming.internal.StopReason
import com.amplitude.core.AmplitudePreview

@OptIn(AmplitudePreview::class)
internal class StreamSession(
    val streamSessionId: String,
    val startedInsertId: String,
    val stoppedInsertId: String,
    val playId: String,
    val startTimeMillis: Long,
    val options: PlayerContent,
    val mediaType: MediaType,
    snapshot: PlayerMediaSnapshot,
    /**
     * Play time already accrued by earlier plays of the same stream session.
     *
     * `play_time` is cumulative per `stream_session_id`. A pause starts a new play but keeps
     * counting from the total so far. Seeks and buffering stay on the same play.
     */
    private val playTimeBeforeMillis: Long = 0L,
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
    private var playTimeMillis = 0L
    private var lastPlayheadMillis: Long? = snapshot.positionMillis
    private var tracking = false

    @Synchronized
    fun resumeWatch(positionMillis: Long = snapshot.positionMillis) {
        if (frozen) return
        if (tracking) {
            accrue(positionMillis)
            return
        }
        lastPlayheadMillis = positionMillis
        tracking = true
    }

    @Synchronized
    fun pauseWatch(positionMillis: Long = snapshot.positionMillis) {
        if (!tracking) return
        accrue(positionMillis)
        tracking = false
    }

    /**
     * Moves the playhead baseline to [positionMillis] without counting the jump as play time.
     */
    @Synchronized
    fun noteSeek(positionMillis: Long) {
        if (frozen) return
        lastPlayheadMillis = positionMillis
    }

    @Synchronized
    fun durationMillis(): Long {
        if (frozen) return frozenDurationMillis
        return playTimeBeforeMillis + playTimeMillis
    }

    @Synchronized
    fun updateSnapshot(snapshot: PlayerMediaSnapshot) {
        if (frozen) return
        if (tracking) accrue(snapshot.positionMillis)
        this.snapshot = snapshot
    }

    @Synchronized
    fun freeze(snapshot: PlayerMediaSnapshot) {
        if (frozen) return
        pauseWatch(snapshot.positionMillis)
        this.snapshot = snapshot
        frozenDurationMillis = durationMillis()
        frozen = true
    }

    private fun accrue(positionMillis: Long) {
        val last = lastPlayheadMillis
        if (last != null) {
            playTimeMillis += (positionMillis - last).coerceAtLeast(0)
        }
        lastPlayheadMillis = positionMillis
    }
}
