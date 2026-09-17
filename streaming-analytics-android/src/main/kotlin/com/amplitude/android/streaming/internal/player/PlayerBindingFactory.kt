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

internal val StreamingDiGraph.playerBindingFactory: PlayerBindingFactory by DiGraph.singleton {
    PlayerBindingFactory(
        playerObserverFactory = playerObserverFactory,
        streamTracker = streamTracker,
        heartbeatFactory = heartbeatFactory,
        time = time,
        scope = scope,
        playerDispatcherFactory = playerDispatcherFactory,
    )
}

@OptIn(AmplitudePreview::class)
internal class PlayerBindingFactory(
    private val playerObserverFactory: PlayerObserverFactory,
    private val streamTracker: StreamTracker,
    private val heartbeatFactory: HeartbeatFactory,
    private val time: Time,
    private val scope: CoroutineScope,
    private val playerDispatcherFactory: PlayerDispatcherFactory,
) {
    private val lock = Any()
    private val bindingRegistry = mutableListOf<PlayerBinding>()

    fun getOrCreate(
        player: Player,
        contentProvider: PlayerContentProvider,
    ): PlayerBinding {
        val orphaned: List<PlayerBinding>
        val binding: PlayerBinding
        val created: Boolean
        synchronized(lock) {
            orphaned = bindingRegistry.filter { it.isOrphaned() }
            bindingRegistry.removeAll(orphaned.toSet())
            val existing = bindingRegistry.firstOrNull { it.isBoundTo(player) }
            if (existing != null) {
                binding = existing
                created = false
            } else {
                binding =
                    PlayerBinding(
                        player = player,
                        contentProvider = contentProvider,
                        playerObserverFactory = playerObserverFactory,
                        streamTracker = streamTracker,
                        heartbeatFactory = heartbeatFactory,
                        time = time,
                        parentScope = scope,
                        playerDispatcher = playerDispatcherFactory.create(player),
                        onStopped = ::unregister,
                    ).also { bindingRegistry.add(it) }
                created = true
            }
            if (created) binding.start()
        }
        orphaned.forEach { it.stop() }
        return binding
    }

    fun detach(player: Player) {
        val orphaned: List<PlayerBinding>
        val binding: PlayerBinding?
        synchronized(lock) {
            orphaned = bindingRegistry.filter { it.isOrphaned() }
            bindingRegistry.removeAll(orphaned.toSet())
            binding = bindingRegistry.firstOrNull { it.isBoundTo(player) }
            if (binding != null) {
                bindingRegistry.remove(binding)
            }
        }
        orphaned.forEach { it.stop() }
        binding?.stop()
    }

    fun detachAll() {
        synchronized(lock) {
            val toFlush = bindingRegistry.toList()
            bindingRegistry.clear()
            toFlush
        }.forEach { it.stop() }
    }

    private fun unregister(binding: PlayerBinding) {
        synchronized(lock) {
            bindingRegistry.remove(binding)
        }
    }
}
