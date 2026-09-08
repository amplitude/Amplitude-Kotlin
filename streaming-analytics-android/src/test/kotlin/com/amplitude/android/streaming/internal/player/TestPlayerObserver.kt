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

    override suspend fun snapshot(): PlayerMediaSnapshot =
        PlayerMediaSnapshot(
            positionMillis = POSITION_MILLIS,
            durationMillis = DURATION_MILLIS,
            mediaType = MediaType.VIDEO,
        )

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 16
        const val POSITION_MILLIS = 1_000L
        const val DURATION_MILLIS = 10_000L
    }
}
