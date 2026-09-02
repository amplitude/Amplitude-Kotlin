@file:JvmName("AmplitudeStreamingAnalytics")

package com.amplitude.android

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.internal.StreamingAnalytics
import com.amplitude.core.AmplitudePreview

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
    StreamingAnalytics.from(this)
        .trackPlayer(
            player = player,
            contentProvider = contentProvider,
        )
}
