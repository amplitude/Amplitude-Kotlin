@file:JvmName("AmplitudeStreamingAnalytics")

package com.amplitude.android

import androidx.media3.common.Player
import com.amplitude.android.streaming.PlayerContentProvider
import com.amplitude.android.streaming.StreamingAnalyticsPlugin
import com.amplitude.core.Amplitude
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
    val plugin = resolveStreamingAnalyticsPlugin(findPlugin())
    plugin.streamingAnalytics?.trackPlayer(
        player = player,
        contentProvider = contentProvider,
    )
}

/**
 * Returns the registered [StreamingAnalyticsPlugin], installing one if needed.
 *
 * Plugin registration is first-name-wins. If another thread already registered the
 * plugin, [Amplitude.add] keeps that instance; resolve it instead of using a rejected
 * candidate that was never set up.
 */
@OptIn(AmplitudePreview::class)
internal fun Amplitude.resolveStreamingAnalyticsPlugin(
    existing: StreamingAnalyticsPlugin?,
): StreamingAnalyticsPlugin {
    existing?.let { return it }
    val candidate = StreamingAnalyticsPlugin()
    add(candidate)
    return plugin(candidate.name) as? StreamingAnalyticsPlugin ?: candidate
}
