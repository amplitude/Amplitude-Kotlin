package com.amplitude.android.streaming.internal.player

import kotlinx.coroutines.flow.SharedFlow

internal interface PlayerObserver {
    val eventFlow: SharedFlow<PlayerEvent>

    suspend fun snapshot(): PlayerMediaSnapshot
}
