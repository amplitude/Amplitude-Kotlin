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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val mutex = Mutex()
    private val bindingRegistry = mutableListOf<PlayerBinding>()
    private var closed = false

    suspend fun getOrCreate(
        player: Player,
        contentProvider: PlayerContentProvider,
    ): PlayerBinding? =
        mutex.withLock {
            if (closed) return@withLock null
            sweepInactive()
            val existing = bindingRegistry.firstOrNull { it.isBoundTo(player) }
            if (existing != null) {
                return@withLock existing
            }
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
            ).also {
                bindingRegistry.add(it)
                it.start()
            }
        }

    suspend fun detach(player: Player) {
        mutex.withLock {
            sweepInactive()
            val binding = bindingRegistry.firstOrNull { it.isBoundTo(player) }
            if (binding != null) {
                bindingRegistry.remove(binding)
            }
            binding?.stopAndJoin()
        }
    }

    suspend fun detachAll() {
        mutex.withLock {
            closed = true
            val toFlush = bindingRegistry.toList()
            bindingRegistry.clear()
            toFlush.forEach { it.stopAndJoin() }
        }
    }

    internal suspend fun trackedCount(): Int = mutex.withLock { bindingRegistry.size }

    private suspend fun sweepInactive() {
        val inactive = bindingRegistry.filter { it.isOrphaned() || it.hasStopped() }
        bindingRegistry.removeAll(inactive.toSet())
        inactive.forEach { it.stopAndJoin() }
    }

    private fun unregister(binding: PlayerBinding) {
        scope.launch {
            mutex.withLock {
                bindingRegistry.remove(binding)
            }
        }
    }
}
