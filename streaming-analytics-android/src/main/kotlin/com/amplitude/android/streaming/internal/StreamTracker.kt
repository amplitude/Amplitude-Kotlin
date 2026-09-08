package com.amplitude.android.streaming.internal

import androidx.media3.common.C
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.internal.player.PlayerMediaSnapshot
import com.amplitude.android.streaming.internal.player.PlayerState
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.android.streaming.internal.util.millisToSeconds
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudePreview

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
    fun trackAdStarted(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
    ) {
        amplitude.track(
            eventType = AD_STARTED,
            eventProperties =
                adProperties(
                    options = options,
                    ad = ad,
                    streamSessionId = streamSessionId,
                ),
        )
    }

    fun trackAdStopped(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
        watchDurationMillis: Long,
        status: AdCompletionStatus,
    ) {
        amplitude.track(
            eventType = AD_STOPPED,
            eventProperties =
                adProperties(options = options, ad = ad, streamSessionId = streamSessionId).apply {
                    put("ad_watch_duration", watchDurationMillis.millisToSeconds())
                    put("ad_completion_status", status.value)
                    ad.percentWatched(watchDurationMillis)?.let { percentage ->
                        put("ad_percent_completed", percentage)
                    }
                },
        )
    }

    fun trackAdSkipped(
        options: PlayerContent,
        ad: AdContext,
        streamSessionId: String,
    ) {
        amplitude.track(
            eventType = AD_SKIPPED,
            eventProperties =
                adProperties(
                    options = options,
                    ad = ad,
                    streamSessionId = streamSessionId,
                ).apply {
                    put("skip_position", ad.positionMillis.millisToSeconds())
                },
        )
    }

    fun trackStreamStarted(
        options: PlayerContent,
        snapshot: PlayerMediaSnapshot,
        playerState: PlayerState,
        mediaType: MediaType,
        streamSessionId: String,
        playId: String,
        startTimeMillis: Long,
        timestamp: Long,
        insertId: String,
    ) {
        amplitude.track(
            event =
                DelayedEvent(
                    eventType = STREAM_STARTED,
                    kind = DelayedEvent.Kind.INSTANT,
                    timestamp = timestamp,
                    eventProperties =
                        contentProperties(
                            options = options,
                            snapshot = snapshot,
                            playerState = playerState,
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
        playerState: PlayerState,
        mediaType: MediaType,
        streamSessionId: String,
        playId: String,
        startTimeMillis: Long,
        streamDurationMillis: Long,
        timestamp: Long,
        insertId: String,
        stopReason: StopReason? = null,
        errorMessage: String? = null,
    ) {
        amplitude.track(
            event =
                DelayedEvent(
                    eventType = STREAM_STOPPED,
                    kind = stopReason.eventKind(),
                    timestamp = timestamp,
                    eventProperties =
                        stoppedContentProperties(
                            options = options,
                            snapshot = snapshot,
                            playerState = playerState,
                            mediaType = mediaType,
                            streamSessionId = streamSessionId,
                            playId = playId,
                            startTimeMillis = startTimeMillis,
                            streamDurationMillis = streamDurationMillis,
                            stopReason = stopReason,
                            errorMessage = errorMessage,
                        ),
                ).also { it.insertId = insertId },
        )
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
    playerState: PlayerState,
    mediaType: MediaType,
    streamSessionId: String,
    playId: String,
    startTimeMillis: Long,
): MutableMap<String, Any?> =
    options.extraProperties.orEmpty().toMutableMap().apply {
        put("stream_session_id", streamSessionId)
        put("play_id", playId)
        put("media_type", mediaType.value)
        (options.contentId ?: snapshot.mediaId)?.let { put("content_id", it) }
        (options.title ?: snapshot.title)?.let { put("title", it) }
        put("delivery_mode", deliveryMode(options = options, snapshot = snapshot))
        put("is_in_picture_in_picture", playerState.isInPictureInPicture)
        put("is_in_background", playerState.isInBackground)
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
    playerState: PlayerState,
    mediaType: MediaType,
    streamSessionId: String,
    playId: String,
    startTimeMillis: Long,
    streamDurationMillis: Long,
    stopReason: StopReason?,
    errorMessage: String?,
): MutableMap<String, Any?> =
    contentProperties(
        options = options,
        snapshot = snapshot,
        playerState = playerState,
        mediaType = mediaType,
        streamSessionId = streamSessionId,
        playId = playId,
        startTimeMillis = startTimeMillis,
    ).apply {
        put("stream_duration", streamDurationMillis.millisToSeconds())
        stopReason?.let { put("stop_reason", it.value) }
        errorMessage?.let { put("error_message", it) }
        snapshot.percentCompleted()?.let { percentage ->
            put("percent_completed", percentage)
        }
    }

@OptIn(AmplitudePreview::class)
private fun deliveryMode(
    options: PlayerContent,
    snapshot: PlayerMediaSnapshot,
): String =
    when (options.deliveryMode) {
        PlayerContent.DELIVERY_MODE_LIVE -> PlayerContent.DELIVERY_MODE_LIVE
        PlayerContent.DELIVERY_MODE_ON_DEMAND -> PlayerContent.DELIVERY_MODE_ON_DEMAND
        else ->
            if (snapshot.isLive) {
                PlayerContent.DELIVERY_MODE_LIVE
            } else {
                PlayerContent.DELIVERY_MODE_ON_DEMAND
            }
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
) {
    val adId: String
        get() = "${contentId.orEmpty()}:$adGroupIndex:$adIndexInAdGroup"
}

internal fun AdContext.percentWatched(watchDurationMillis: Long): Double? {
    if (!durationMillis.isKnownDuration()) {
        return null
    }
    if (durationMillis == 0L) {
        return 0.0
    }
    return (100.0 * watchDurationMillis.toDouble() / durationMillis)
        .coerceIn(0.0, 100.0)
}

internal enum class AdCompletionStatus(
    val value: String,
) {
    COMPLETED("completed"),
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
    COMPLETED("completed"),
    SEEKING("seeking"),
    WAITING("waiting"),
    ERROR("error"),
    UNTRACKED("untracked"),
}

private fun StopReason?.eventKind(): DelayedEvent.Kind =
    if (this == StopReason.TIMEOUT) {
        DelayedEvent.Kind.DELAYED
    } else {
        DelayedEvent.Kind.INSTANT
    }
