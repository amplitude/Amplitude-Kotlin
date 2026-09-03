package com.amplitude.android.streaming

import androidx.media3.common.MediaItem
import com.amplitude.core.AmplitudePreview

/**
 * Supplies [PlayerContent] for the current media item.
 *
 * Invoked for the item already loaded when tracking starts, and again on each media
 * transition. Return quickly; do not block.
 *
 * ```
 * amplitude.trackPlayer(exoPlayer) { mediaItem ->
 *     PlayerContent(
 *         contentId = mediaItem?.mediaId,
 *         title = "Episode 1",
 *     )
 * }
 * ```
 */
@AmplitudePreview
public fun interface PlayerContentProvider {
    /**
     * Content for [mediaItem], or defaults when the player has no current item.
     */
    public fun optionsFor(mediaItem: MediaItem?): PlayerContent
}
