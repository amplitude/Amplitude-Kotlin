package com.amplitude.android.streaming.internal.player

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.streamTracker
import com.amplitude.android.streaming.internal.util.DiGraph
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.android.streaming.internal.util.time
import com.amplitude.core.AmplitudePreview
import kotlinx.coroutines.CoroutineScope
import java.util.IdentityHashMap

internal val StreamingDiGraph.playerBindingFactory: PlayerBindingFactory by DiGraph.singleton {
    PlayerBindingFactory(
        playerObserverFactory = playerObserverFactory,
        streamTracker = streamTracker,
        time = time,
        scope = scope,
    )
}

@OptIn(AmplitudePreview::class)
internal class PlayerBindingFactory(
    private val playerObserverFactory: PlayerObserverFactory,
    private val streamTracker: StreamTracker,
    private val time: Time,
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val bindingRegistry = IdentityHashMap<Player, PlayerBinding>()

    fun getOrCreate(
        player: Player,
        contentProvider: PlayerContentProvider,
    ): PlayerBinding {
        synchronized(lock) {
            bindingRegistry[player]?.let { return it }
            val binding =
                PlayerBinding(
                    player = player,
                    contentProvider = contentProvider,
                    playerObserverFactory = playerObserverFactory,
                    streamTracker = streamTracker,
                    time = time,
                    parentScope = scope,
                )
            bindingRegistry[player] = binding
            binding.start()
            return binding
        }
    }

    fun detachAll() {
        synchronized(lock) {
            val toFlush = bindingRegistry.values.toList()
            bindingRegistry.clear()
            toFlush
        }.forEach { it.stop() }
    }
}
