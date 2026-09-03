package com.amplitude.android.streaming

import com.amplitude.core.AmplitudePreview

/**
 * App-supplied metadata for `trackPlayer`.
 *
 * The library infers position, duration, errors, and ended/buffering from the player.
 * Pass [contentId], [title], and [deliveryMode] when the app already knows them.
 *
 * @param contentId Stable id for the current media item (`content_id` on events).
 * @param title Human-readable title.
 * @param deliveryMode One of [DELIVERY_MODE_LIVE] or [DELIVERY_MODE_ON_DEMAND] when known.
 * @param extraProperties App extras merged onto started/stopped events.
 *
 * ```
 * PlayerContent(
 *     contentId = "ep-1",
 *     title = "Episode 1",
 *     deliveryMode = PlayerContent.DELIVERY_MODE_ON_DEMAND,
 * )
 * ```
 */
@AmplitudePreview
public class PlayerContent @JvmOverloads constructor(
    public val contentId: String? = null,
    public val title: String? = null,
    public val deliveryMode: String? = null,
    extraProperties: Map<String, Any?>? = null,
) {
    /**
     * Defensive copy so callers can mutate the map they passed without racing the tracker.
     */
    public val extraProperties: Map<String, Any?>? = extraProperties?.toMap()

    public companion object {
        public const val DELIVERY_MODE_LIVE: String = "live"
        public const val DELIVERY_MODE_ON_DEMAND: String = "on_demand"
    }
}
