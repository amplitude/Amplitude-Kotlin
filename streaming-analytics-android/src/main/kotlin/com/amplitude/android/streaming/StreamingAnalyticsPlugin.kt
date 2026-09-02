package com.amplitude.android.streaming

import com.amplitude.android.streaming.internal.StreamingAnalytics
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin

private const val NAME: String = "AmplitudeStreamingAnalytics"

/**
 * Amplitude plugin for Streaming Analytics.
 *
 * Installed automatically when `streaming-analytics-android` is on the classpath.
 * [teardown] calls [StreamingAnalytics.teardown].
 *
 * ```
 * amplitude.trackPlayer(exoPlayer) { mediaItem ->
 *     PlayerContent(contentId = mediaItem?.mediaId)
 * }
 * ```
 */
@AmplitudePreview
public class StreamingAnalyticsPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override val name: String = NAME
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent? {
        if (event !is DelayedEvent) {
            return event
        }
        // TODO: Queue DelayedEvent
        return null
    }

    override fun teardown() {
        StreamingAnalytics.teardown(amplitude)
    }
}
