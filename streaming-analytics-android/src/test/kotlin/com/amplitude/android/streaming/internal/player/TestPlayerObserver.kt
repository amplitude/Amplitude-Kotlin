package com.amplitude.android.streaming.internal.player

import com.amplitude.android.streaming.internal.MediaType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A [PlayerObserver] whose event flow is driven by the test instead of a real player.
 */
internal class TestPlayerObserver : PlayerObserver {
    private val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)

    override val eventFlow = events.asSharedFlow()

    fun emit(event: PlayerEvent) {
        check(events.tryEmit(event))
    }

    var snapshotError: Throwable? = null
    var positionMillis: Long = POSITION_MILLIS

    override suspend fun snapshot(): PlayerMediaSnapshot {
        snapshotError?.let { throw it }
        return PlayerMediaSnapshot(
            positionMillis = positionMillis,
            durationMillis = DURATION_MILLIS,
            mediaType = MediaType.VIDEO,
        )
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 16
        const val POSITION_MILLIS = 1_000L
        const val DURATION_MILLIS = 10_000L
    }
}
