package com.amplitude.android.streaming.internal

import androidx.media3.common.C
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.player.PlayerMediaSnapshot
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.android.streaming.internal.util.millisToSeconds
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview
import com.amplitude.core.platform.Plugin
import java.util.concurrent.atomic.AtomicReference

private const val AD_STARTED = "[Amplitude] Ad Started"
private const val AD_STOPPED = "[Amplitude] Ad Stopped"
private const val AD_SKIPPED = "[Amplitude] Ad Skipped"
private const val STREAM_STARTED = "[Amplitude] Stream Started"
private const val STREAM_STOPPED = "[Amplitude] Stream Stopped"

internal val StreamingDiGraph.streamTracker: StreamTracker by weak {
    StreamTracker(
        amplitude = amplitude,
    )
}

@OptIn(AmplitudePreview::class)
internal class StreamTracker(
    private val amplitude: Amplitude,
) {
    companion object {
        /**
         * Ad events stay off until further validation.
         */
        var adsEventsEnabled: Boolean = false
    }

    private val delayedEventSink = AtomicReference<((DelayedEvent) -> Unit)?>(null)

    /**
     * Sends delayed events to [sink] instead of tracking them on the timeline.
     *
     * Teardown needs this: the timeline removes [com.amplitude.android.streaming.StreamingAnalyticsPlugin]
     * before it calls the plugin's teardown, so a delayed event tracked from then on would reach
     * the standard event destination instead of the delayed-events pipeline.
     */
    fun routeDelayedEvents(sink: (DelayedEvent) -> Unit) {
        delayedEventSink.set(sink)
    }

    fun trackAdStarted(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
        timestamp: Long,
        insertId: String,
    ) {
        if (!adsEventsEnabled) return
        trackDelayed(
            DelayedEvent(
                eventType = AD_STARTED,
                kind = DelayedEvent.Kind.INSTANT,
                timestamp = timestamp,
                eventProperties =
                    adProperties(
                        options = options,
                        ad = ad,
                        streamSessionId = streamSessionId,
                    ),
            ).also { it.insertId = insertId },
        )
    }

    fun trackAdStopped(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
        playTimeMillis: Long,
        status: AdCompletionStatus,
        timestamp: Long,
        insertId: String,
    ) {
        if (!adsEventsEnabled) return
        trackDelayed(
            DelayedEvent(
                eventType = AD_STOPPED,
                kind = status.eventKind(),
                timestamp = timestamp,
                eventProperties =
                    adProperties(options = options, ad = ad, streamSessionId = streamSessionId).apply {
                        put("ad_play_time", playTimeMillis.millisToSeconds())
                        put("ad_completion_status", status.value)
                        ad.percentWatched(playTimeMillis)?.let { percentage ->
                            put("ad_percent_completed", percentage)
                        }
                    },
            ).also { it.insertId = insertId },
        )
    }

    fun trackAdSkipped(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
        timestamp: Long,
        insertId: String,
    ) {
        if (!adsEventsEnabled) return
        trackDelayed(
            DelayedEvent(
                eventType = AD_SKIPPED,
                kind = DelayedEvent.Kind.INSTANT,
                timestamp = timestamp,
                eventProperties =
                    adProperties(
                        options = options,
                        ad = ad,
                        streamSessionId = streamSessionId,
                    ).apply {
                        put("skip_position", ad.positionMillis.millisToSeconds())
                    },
            ).also { it.insertId = insertId },
        )
    }

    fun trackStreamStarted(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        mediaType: MediaType,
        streamSessionId: String,
        playId: String,
        startTimeMillis: Long,
        timestamp: Long,
        insertId: String,
    ) {
        trackDelayed(
            DelayedEvent(
                eventType = STREAM_STARTED,
                kind = DelayedEvent.Kind.INSTANT,
                timestamp = timestamp,
                eventProperties =
                    contentProperties(
                        options = options,
                        snapshot = snapshot,
                        mediaType = mediaType,
                        streamSessionId = streamSessionId,
                        playId = playId,
                        startTimeMillis = startTimeMillis,
                    ),
            ).also { it.insertId = insertId },
        )
    }

    fun trackStreamStopped(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        mediaType: MediaType,
        streamSessionId: String,
        playId: String,
        startTimeMillis: Long,
        playTimeMillis: Long,
        timestamp: Long,
        insertId: String,
        stopReason: StopReason? = null,
        errorMessage: String? = null,
    ) {
        trackDelayed(
            DelayedEvent(
                eventType = STREAM_STOPPED,
                kind = stopReason.eventKind(),
                timestamp = timestamp,
                eventProperties =
                    stoppedContentProperties(
                        options = options,
                        snapshot = snapshot,
                        mediaType = mediaType,
                        streamSessionId = streamSessionId,
                        playId = playId,
                        startTimeMillis = startTimeMillis,
                        playTimeMillis = playTimeMillis,
                        stopReason = stopReason,
                        errorMessage = errorMessage,
                    ),
            ).also { it.insertId = insertId },
        )
    }

    private fun trackDelayed(event: DelayedEvent) {
        val sink = delayedEventSink.get()
        if (sink == null) {
            amplitude.track(event)
            return
        }
        if (amplitude.optOut) return
        // Stands in for the enrichment the timeline would have applied on the way to the plugin.
        event.sessionId = event.sessionId ?: amplitude.sessionId
        if (amplitude.timeline.applyPlugins(Plugin.Type.Before, event) == null) return
        sink(event)
    }
}

@OptIn(AmplitudePreview::class)
private fun adProperties(
    options: PlayerContent,
    ad: AdContext,
    streamSessionId: String,
): MutableMap<String, Any?> =
    options.extraProperties.orEmpty().toMutableMap().apply {
        put("ad_id", ad.adId)
        put("content_id", options.contentId ?: ad.contentId)
        put("stream_session_id", streamSessionId)
        put("ad_position", ad.contentPositionMillis.millisToSeconds())
        if (ad.durationMillis.isKnownDuration()) {
            put("ad_duration", ad.durationMillis.millisToSeconds())
        }
    }

@OptIn(AmplitudePreview::class)
private fun contentProperties(
    options: PlayerContent,
    snapshot: PlayerMediaSnapshot,
    mediaType: MediaType,
    streamSessionId: String,
    playId: String,
    startTimeMillis: Long,
): MutableMap<String, Any?> =
    options.extraProperties.orEmpty().toMutableMap().apply {
        put("stream_session_id", streamSessionId)
        put("play_id", playId)
        put("media_type", mediaType.value)
        (options.contentId ?: snapshot.mediaId)?.takeIf { it.isNotBlank() }?.let { put("content_id", it) }
        (options.title ?: snapshot.title)?.let { put("title", it) }
        put("delivery_mode", snapshot.deliveryMode())
        if (snapshot.hasKnownDuration()) {
            put("duration", snapshot.durationMillis.millisToSeconds())
        }
        put("start_time", startTimeMillis.millisToSeconds())
        put("position", snapshot.positionMillis.millisToSeconds())
    }

@OptIn(AmplitudePreview::class)
private fun stoppedContentProperties(
    options: PlayerContent,
    snapshot: PlayerMediaSnapshot,
    mediaType: MediaType,
    streamSessionId: String,
    playId: String,
    startTimeMillis: Long,
    playTimeMillis: Long,
    stopReason: StopReason?,
    errorMessage: String?,
): MutableMap<String, Any?> =
    contentProperties(
        options = options,
        snapshot = snapshot,
        mediaType = mediaType,
        streamSessionId = streamSessionId,
        playId = playId,
        startTimeMillis = startTimeMillis,
    ).apply {
        put("play_time", playTimeMillis.millisToSeconds())
        stopReason?.let { put("stop_reason", it.value) }
        errorMessage?.let { put("error_message", it) }
        snapshot.percentCompleted()?.let { percentage ->
            put("percent_completed", percentage)
        }
    }

@OptIn(AmplitudePreview::class)
private fun PlayerMediaSnapshot.deliveryMode(): String =
    if (isLive) {
        PlayerContent.DELIVERY_MODE_LIVE
    } else {
        PlayerContent.DELIVERY_MODE_ON_DEMAND
    }

private fun PlayerMediaSnapshot.hasKnownDuration(): Boolean =
    !isLive && durationMillis.isKnownDuration()

private fun Long.isKnownDuration(): Boolean = this != C.TIME_UNSET && this >= 0

private fun PlayerMediaSnapshot.percentCompleted(): Double? {
    if (!hasKnownDuration()) {
        return null
    }
    if (durationMillis == 0L) {
        return 0.0
    }
    return (positionMillis.toDouble() / durationMillis.toDouble() * 100.0)
        .coerceIn(0.0, 100.0)
}

internal data class AdContext(
    val adGroupIndex: Int,
    val adIndexInAdGroup: Int,
    val positionMillis: Long,
    val durationMillis: Long,
    val contentPositionMillis: Long,
    val contentId: String?,
    val mediaItemIndex: Int,
) {
    val adId: String
        get() = "${contentId.orEmpty()}:$mediaItemIndex:$adGroupIndex:$adIndexInAdGroup"
}

internal fun AdContext.percentWatched(playTimeMillis: Long): Double? {
    if (!durationMillis.isKnownDuration()) {
        return null
    }
    if (durationMillis == 0L) {
        return 0.0
    }
    return (100.0 * playTimeMillis.toDouble() / durationMillis)
        .coerceIn(0.0, 100.0)
}

internal enum class AdCompletionStatus(
    val value: String,
) {
    TIMEOUT("timeout"),
    ENDED("ended"),
    SKIPPED("skipped"),
    ABANDONED("abandoned"),
}

internal enum class MediaType(
    val value: String,
) {
    VIDEO("video"),
    AUDIO("audio"),
}

internal enum class StopReason(
    val value: String,
) {
    TIMEOUT("timeout"),
    PAUSED("paused"),
    ENDED("ended"),
    ERROR("error"),
    UNTRACKED("untracked"),
    CONTENT_CHANGED("content_changed"),
}

private fun StopReason?.eventKind(): DelayedEvent.Kind =
    if (this == StopReason.TIMEOUT) {
        DelayedEvent.Kind.DELAYED
    } else {
        DelayedEvent.Kind.INSTANT
    }

private fun AdCompletionStatus.eventKind(): DelayedEvent.Kind =
    if (this == AdCompletionStatus.TIMEOUT) {
        DelayedEvent.Kind.DELAYED
    } else {
        DelayedEvent.Kind.INSTANT
    }
