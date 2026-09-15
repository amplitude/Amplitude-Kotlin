package com.amplitude.android.streaming.internal.player

import android.os.Handler
import androidx.media3.common.Player
import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher

internal val StreamingDiGraph.playerDispatcherFactory: PlayerDispatcherFactory by DiGraph.weak {
    PlayerDispatcherFactory()
}

internal class PlayerDispatcherFactory {
    fun create(player: Player): CoroutineDispatcher {
        return Handler(player.applicationLooper).asCoroutineDispatcher()
    }
}
