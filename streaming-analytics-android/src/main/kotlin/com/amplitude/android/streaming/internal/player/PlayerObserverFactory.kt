package com.amplitude.android.streaming.internal.player

import androidx.media3.common.Player
import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import kotlinx.coroutines.CoroutineScope

internal val StreamingDiGraph.playerObserverFactory: PlayerObserverFactory by weak {
    PlayerObserverFactory { player, parentScope ->
        Media3PlayerObserver(
            player = player,
            scope = parentScope,
        )
    }
}

internal fun interface PlayerObserverFactory {
    fun create(
        player: Player,
        parentScope: CoroutineScope,
    ): PlayerObserver
}
