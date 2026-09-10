@file:JvmName("AmplitudeStreamingAnalytics")

package com.amplitude.android

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.StreamingAnalyticsPlugin
import com.amplitude.core.AmplitudePreview
import kotlinx.coroutines.launch

/**
 * Starts Streaming Analytics on [androidx.media3.common.Player].
 *
 * If [player] is already tracked by this Amplitude instance, this function is a no-op
 * and [contentProvider] is ignored.
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
    amplitudeScope.launch(amplitudeDispatcher) {
        isBuilt.await()
        val streamingAnalytics = findPlugin<StreamingAnalyticsPlugin>()?.streamingAnalytics
        if (streamingAnalytics == null) {
            logger.error("StreamingAnalyticsPlugin is not installed.")
            return@launch
        }
        streamingAnalytics.trackPlayer(
            player = player,
            contentProvider = contentProvider,
        )
    }
}
