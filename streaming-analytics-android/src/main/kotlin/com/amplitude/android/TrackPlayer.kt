@file:JvmName("AmplitudeStreamingAnalytics")

package com.amplitude.android

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.StreamingAnalyticsPlugin
import com.amplitude.android.streaming.internal.StreamingAnalytics
import com.amplitude.core.AmplitudePreview
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val playerCommands = Mutex()

/**
 * Starts Streaming Analytics on [androidx.media3.common.Player].
 *
 * If [player] is already tracked by this Amplitude instance, this function is a no-op
 * and [contentProvider] is ignored.
 *
 * The player is held only with a [java.lang.ref.WeakReference], so dropping your own
 * reference does not leak. Call [untrackPlayer] only when you want a terminal stream
 * or ad stop immediately; it is not required to prevent a leak.
 *
 * Tracking starts asynchronously, but [trackPlayer] and [untrackPlayer] apply in the
 * order they are called.
 *
 * ```
 * amplitude.trackPlayer(exoPlayer) { mediaItem ->
 *     PlayerContent(
 *         contentId = mediaItem?.mediaId ?: "ep-1",
 *         title = "Episode 1",
 *     )
 * }
 * ```
 */
@AmplitudePreview
public fun Amplitude.trackPlayer(
    player: Player,
    contentProvider: PlayerContentProvider,
) {
    launchPlayerCommand { trackPlayer(player, contentProvider) }
}

/**
 * Stops Streaming Analytics on [player] and emits a terminal stream/ad stop if a session
 * is open.
 *
 * Optional. The SDK does not retain a strong reference to [player], so skipping this
 * call does not leak the player. Use it when you want tracking to end now instead of
 * when the player becomes unreachable.
 *
 * If [player] is not tracked by this Amplitude instance, this function is a no-op.
 * Safe to call more than once.
 *
 * Applied after a [trackPlayer] that is still in flight, so the stream that call opened
 * is the stream this one stops.
 *
 * ```
 * amplitude.untrackPlayer(exoPlayer)
 * exoPlayer.release()
 * ```
 */
@AmplitudePreview
public fun Amplitude.untrackPlayer(player: Player) {
    launchPlayerCommand { untrackPlayer(player) }
}

/**
 * Applies [command] after the track/untrack commands called before it.
 *
 * [CoroutineStart.UNDISPATCHED] queues on [playerCommands] from the calling thread and
 * [Mutex] wakes its waiters in order, so commands apply in call order even though
 * [Amplitude.amplitudeDispatcher] is free to start them on any thread. The command
 * runs to completion under the lock, including attach and detach.
 */
@OptIn(AmplitudePreview::class)
private fun Amplitude.launchPlayerCommand(command: suspend StreamingAnalytics.() -> Unit) {
    amplitudeScope.launch(amplitudeDispatcher, CoroutineStart.UNDISPATCHED) {
        playerCommands.withLock {
            isBuilt.await()
            val streamingAnalytics = findPlugin<StreamingAnalyticsPlugin>()?.streamingAnalytics
            if (streamingAnalytics == null) {
                logger.error("StreamingAnalyticsPlugin is not installed.")
                return@withLock
            }
            streamingAnalytics.command()
        }
    }
}
