package com.amplitude.android.streaming.internal.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.amplitude.android.streaming.internal.AdContext
import com.amplitude.android.streaming.internal.MediaType
import com.amplitude.android.streaming.internal.StopReason
import com.amplitude.android.streaming.internal.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

private const val BUFFERING_DEBOUNCE_MILLIS = 500L
private const val EVENT_BUFFER_CAPACITY = 64

internal class Media3PlayerObserver(
    player: Player,
    private val scope: CoroutineScope,
    private val playerDispatcher: CoroutineDispatcher,
) : Player.Listener,
    PlayerObserver {
    private val playerReference = WeakReference(player)
    private val _eventFlow =
        MutableSharedFlow<PlayerEvent>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    override val eventFlow: SharedFlow<PlayerEvent> = _eventFlow.asSharedFlow()
    private val mutex = Mutex()
    private var bufferingJob: Job? = null
    private var observing = false
    private var activeAd: AdContext? = null
    private var outgoingSnapshot: PlayerMediaSnapshot? = null
    private var lastSnapshot =
        PlayerMediaSnapshot(
            positionMillis = 0L,
            mediaType = MediaType.VIDEO,
        )

    init {
        scope.launch {
            try {
                _eventFlow.subscriptionCount
                    .map { it > 0 }
                    .distinctUntilChanged()
                    .collect { subscribed ->
                        if (subscribed) attach() else detach()
                    }
            } finally {
                withContext(NonCancellable) {
                    detach()
                }
            }
        }
    }

    override suspend fun snapshot(): PlayerMediaSnapshot? =
        runCatchingCancellable {
            withContext(playerDispatcher) {
                val player = playerReference.get() ?: return@withContext lastSnapshot
                val item = player.currentMediaItem
                val metadata = item?.mediaMetadata
                PlayerMediaSnapshot(
                    positionMillis = player.contentPosition.coerceAtLeast(0L),
                    durationMillis = player.contentDuration,
                    isLive = player.contentIsLive(),
                    mediaId = item?.mediaId?.takeIf { it.isNotEmpty() },
                    title = metadata?.title?.toString() ?: metadata?.displayTitle?.toString(),
                    mediaType = player.mediaType(),
                ).also { lastSnapshot = it }
            }
        }.getOrNull()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        runCatchingCancellable {
            val player = playerReference.get() ?: return@runCatchingCancellable
            if (isPlaying) {
                emit(PlayerEvent.Playing)
            } else if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                // Suppression (audio focus, unsuitable output, scrubbing): still READY and
                // intending to play, but isPlaying flipped false with no pause callback.
                emit(PlayerEvent.Buffering)
            }
        }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        runCatchingCancellable {
            val player = playerReference.get() ?: return@runCatchingCancellable
            if (playWhenReady) {
                when (player.playbackState) {
                    Player.STATE_BUFFERING -> startBufferingDebounce()
                    Player.STATE_READY -> {
                        if (!player.isPlaying) {
                            // Suppressed play: playWhenReady flipped true while output is still blocked.
                            emit(PlayerEvent.Buffering)
                        }
                    }
                }
                return@runCatchingCancellable
            }
            if (player.playbackState != Player.STATE_ENDED) {
                cancelBuffering()
                emit(PlayerEvent.Paused)
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        runCatchingCancellable { handlePlaybackStateChanged(playbackState, emitIdlePause = true) }
    }

    private fun handlePlaybackStateChanged(
        playbackState: Int,
        emitIdlePause: Boolean,
    ) {
        val player = playerReference.get() ?: return
        when (playbackState) {
            Player.STATE_BUFFERING -> startBufferingDebounce()
            Player.STATE_READY -> {
                cancelBuffering()
                emit(PlayerEvent.Ready)
            }
            Player.STATE_ENDED -> {
                cancelBuffering()
                if (activeAd != null) {
                    finishAdForTransition(
                        completed = true,
                        positionMillis = player.currentPosition.coerceAtLeast(0),
                    )
                }
                emit(PlayerEvent.Ended)
            }
            Player.STATE_IDLE -> {
                cancelBuffering()
                // Player.stop() / reset go idle without changing playWhenReady, so pause never fires.
                // After onPlayerError, Media3 goes idle with playWhenReady still true.
                if (emitIdlePause && player.playWhenReady && player.playerError == null) {
                    emit(PlayerEvent.Paused)
                }
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        runCatchingCancellable {
            cancelBuffering()
            emit(PlayerEvent.Error(error.message))
        }
    }

    @OptIn(UnstableApi::class)
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        runCatchingCancellable {
            val mediaChanged =
                oldPosition.mediaItemIndex != newPosition.mediaItemIndex ||
                    oldPosition.mediaItem != newPosition.mediaItem
            if (mediaChanged || reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                outgoingSnapshot = snapshotAtDiscontinuity(oldPosition)
            }
            if (reason == Player.DISCONTINUITY_REASON_SEEK && !mediaChanged) {
                emit(PlayerEvent.Seeking(snapshotAtDiscontinuity(oldPosition)))
            }
            if (oldPosition.adGroupIndex != C.INDEX_UNSET &&
                (
                    newPosition.adGroupIndex == C.INDEX_UNSET ||
                        oldPosition.adGroupIndex != newPosition.adGroupIndex ||
                        oldPosition.adIndexInAdGroup != newPosition.adIndexInAdGroup ||
                        oldPosition.mediaItemIndex != newPosition.mediaItemIndex
                )
            ) {
                finishAdForTransition(
                    completed = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
                    skipped = reason == Player.DISCONTINUITY_REASON_SKIP,
                    positionMillis = oldPosition.positionMs,
                )
            }
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        runCatchingCancellable {
            emit(
                PlayerEvent.MediaChanged(
                    mediaItem = mediaItem,
                    previousSnapshot = outgoingSnapshot ?: lastSnapshot,
                    stopReason = mediaChangeStopReason(reason),
                ),
            )
            outgoingSnapshot = null
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        runCatchingCancellable { detectAdTransition() }
    }

    internal fun detectAdTransition() {
        val player = playerReference.get() ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            if (activeAd != null) {
                finishAdForTransition(
                    completed = true,
                    positionMillis = player.currentPosition.coerceAtLeast(0),
                )
            }
            return
        }
        if (player.isPlayingAd) {
            val current = adContextFromPlayer(player)
            val previous = activeAd
            if (previous != null && !previous.isSameAdAs(current)) {
                finishAdForTransition(completed = false, skipped = false)
            }
            if (activeAd == null) {
                activeAd = current
                emit(PlayerEvent.AdStarted(current))
            }
        } else if (activeAd != null) {
            finishAdForTransition(completed = false, skipped = false)
        }
    }

    internal fun finishAdForTransition(
        completed: Boolean,
        skipped: Boolean = false,
        positionMillis: Long? = null,
    ) {
        val ad = activeAd ?: return
        activeAd = null
        val finalAd = positionMillis?.let { ad.copy(positionMillis = it) } ?: ad
        when {
            completed -> emit(PlayerEvent.AdStopped(finalAd, completed = true))
            skipped -> emit(PlayerEvent.AdSkipped(finalAd))
            else -> emit(PlayerEvent.AdStopped(finalAd, completed = false))
        }
    }

    private suspend fun attach() {
        runCatchingCancellable {
            withContext(playerDispatcher) {
                mutex.withLock {
                    if (observing) return@withLock
                    val player = playerReference.get() ?: return@withLock
                    player.addListener(this@Media3PlayerObserver)
                    observing = true
                    if (player.isPlaying) {
                        emit(PlayerEvent.Playing)
                    }
                    handlePlaybackStateChanged(player.playbackState, emitIdlePause = false)
                    detectAdTransition()
                }
            }
        }
    }

    private suspend fun detach() {
        runCatchingCancellable {
            withContext(playerDispatcher) {
                mutex.withLock {
                    if (observing) {
                        observing = false
                        playerReference.get()?.removeListener(this@Media3PlayerObserver)
                    }
                    activeAd = null
                    cancelBuffering()
                }
            }
        }.onFailure {
            observing = false
            activeAd = null
            cancelBuffering()
        }
    }

    private fun emit(event: PlayerEvent) {
        _eventFlow.tryEmit(event)
    }

    private fun startBufferingDebounce() {
        val player = playerReference.get() ?: return
        if (!player.playWhenReady || bufferingJob?.isActive == true) return
        bufferingJob =
            scope.launch {
                delay(BUFFERING_DEBOUNCE_MILLIS.milliseconds)
                emit(PlayerEvent.Buffering)
            }
    }

    private fun cancelBuffering() {
        bufferingJob?.cancel()
        bufferingJob = null
    }

    @OptIn(UnstableApi::class)
    private fun snapshotAtDiscontinuity(oldPosition: Player.PositionInfo): PlayerMediaSnapshot {
        val oldMetadata = oldPosition.mediaItem?.mediaMetadata
        val positionMillis =
            if (oldPosition.adGroupIndex != C.INDEX_UNSET) {
                oldPosition.contentPositionMs
            } else {
                oldPosition.positionMs
            }
        return lastSnapshot.copy(
            positionMillis = positionMillis.coerceAtLeast(0L),
            mediaId = oldPosition.mediaItem?.mediaId?.takeIf { it.isNotEmpty() }
                ?: lastSnapshot.mediaId,
            title = oldMetadata?.title?.toString()
                ?: oldMetadata?.displayTitle?.toString()
                ?: lastSnapshot.title,
        )
    }

    private fun adContextFromPlayer(player: Player): AdContext =
        AdContext(
            adGroupIndex = player.currentAdGroupIndex,
            adIndexInAdGroup = player.currentAdIndexInAdGroup,
            positionMillis = player.currentPosition.coerceAtLeast(0),
            durationMillis = player.duration,
            contentPositionMillis = player.contentPosition.coerceAtLeast(0),
            contentId = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() },
            mediaItemIndex = player.currentMediaItemIndex,
        )
}

private fun mediaChangeStopReason(reason: Int): StopReason =
    when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT,
        -> StopReason.COMPLETED
        else -> StopReason.CONTENT_CHANGED
    }

private fun AdContext.isSameAdAs(other: AdContext): Boolean =
    adGroupIndex == other.adGroupIndex &&
        adIndexInAdGroup == other.adIndexInAdGroup &&
        contentId == other.contentId &&
        mediaItemIndex == other.mediaItemIndex

private fun Player.contentIsLive(): Boolean {
    val timeline = currentTimeline
    val index = currentMediaItemIndex
    if (timeline.isEmpty || index !in 0 until timeline.windowCount) {
        return isCurrentMediaItemLive
    }
    return timeline.getWindow(index, Timeline.Window()).isLive
}

private fun Player.mediaType(): MediaType {
    val groups = currentTracks.groups
    val hasVideo = groups.any { it.isType(C.TRACK_TYPE_VIDEO) }
    val hasAudio = groups.any { it.isType(C.TRACK_TYPE_AUDIO) }
    return when {
        hasVideo -> MediaType.VIDEO
        hasAudio -> MediaType.AUDIO
        else -> MediaType.VIDEO
    }
}

private fun Tracks.Group.isType(trackType: Int): Boolean = type == trackType
