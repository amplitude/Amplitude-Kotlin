package com.amplitude.android.streaming.internal.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.android.streaming.toPlayerContent
import com.amplitude.android.streaming.internal.AdContext
import com.amplitude.android.streaming.internal.AdCompletionStatus
import com.amplitude.android.streaming.internal.StopReason
import com.amplitude.android.streaming.internal.StreamTracker
import com.amplitude.android.streaming.internal.util.Time
import com.amplitude.android.streaming.internal.util.runCatchingCancellable
import com.amplitude.core.AmplitudePreview
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

private const val ORPHAN_CHECK_MILLIS = 1_000L

/**
 * Turns player events into stream sessions, Stream Started, and ad events.
 */
@kotlin.OptIn(AmplitudePreview::class)
internal class PlayerBinding internal constructor(
    player: Player,
    playerObserverFactory: PlayerObserverFactory,
    private val streamTracker: StreamTracker,
    private val heartbeatFactory: HeartbeatFactory,
    private val time: Time,
    parentScope: CoroutineScope,
    private val playerDispatcher: CoroutineDispatcher,
    private val onStopped: (PlayerBinding) -> Unit = {},
) {
    private val playerReference = WeakReference(player)
    private val scope =
        CoroutineScope(
            playerDispatcher +
                SupervisorJob(parentScope.coroutineContext[Job]),
        )
    private val mutex = Mutex()
    private val observer =
        playerObserverFactory.create(
            player = player,
            parentScope = scope,
            playerDispatcher = playerDispatcher,
        )
    private var eventJob: Job? = null
    private var playback: PlaybackState = PlaybackState.Idle()
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)
    private val stoppedCompletion: CompletableJob = Job()

    private var options: PlayerContent = PlayerContent()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatchingCancellable {
                if (stopped.get() || eventJob != null) return@runCatchingCancellable
                options = playerReference.get()?.currentMediaItem?.toPlayerContent() ?: PlayerContent()
                eventJob =
                    scope.launch {
                        observer.eventFlow.collect { event ->
                            if (stopped.get()) return@collect
                            mutex.withLock {
                                if (stopped.get()) return@withLock
                                runCatchingCancellable { handlePlayerEvent(event) }
                            }
                        }
                    }
            }
        }
        scope.launch {
            while (!stopped.get()) {
                delay(ORPHAN_CHECK_MILLIS.milliseconds)
                if (isOrphaned()) stop()
            }
        }
    }

    /**
     * Starts the terminal stop once and returns a job completing when its events are tracked.
     *
     * The stop runs on a scope of its own, independent of both the graph and the caller, so
     * cancelling either cannot leave a stream session without its Stream Stopped.
     */
    fun stop(): Job {
        if (!stopped.compareAndSet(false, true)) return stoppedCompletion
        val rewriteIdleLastSegment = !isOrphaned()
        eventJob?.cancel()
        eventJob = null
        val cleanupScope = CoroutineScope(scope.coroutineContext.minusKey(Job))
        cleanupScope.launch {
            try {
                mutex.withLock {
                    finishPlayback(
                        StopReason.UNTRACKED,
                        rewriteIdleLastSegment = rewriteIdleLastSegment,
                    )
                }
            } finally {
                this@PlayerBinding.scope.cancel()
                stoppedCompletion.complete()
                cleanupScope.cancel()
                onStopped(this@PlayerBinding)
            }
        }
        return stoppedCompletion
    }

    internal suspend fun stopAndJoin() {
        stop().join()
    }

    private suspend fun handlePlayerEvent(event: PlayerEvent) {
        if (isOrphaned()) {
            stop()
            return
        }
        when (event) {
            PlayerEvent.Playing -> onPlaying()
            PlayerEvent.Paused -> onPaused()
            PlayerEvent.Buffering -> onBuffering()
            PlayerEvent.Ready -> onReady()
            PlayerEvent.Ended -> finishSession(StopReason.ENDED)
            is PlayerEvent.Seeking -> onSeeking(event)
            is PlayerEvent.Error -> finishSession(StopReason.ERROR, event.message)
            is PlayerEvent.MediaChanged -> {
                freezeCurrentSegment(event.previousSnapshot)
                finishSession(event.stopReason)
                options = event.mediaItem?.toPlayerContent() ?: PlayerContent()
                if (playerReference.get()?.isPlaying == true) onPlaying()
            }
            is PlayerEvent.AdStarted -> onAdStarted(event.ad)
            is PlayerEvent.AdStopped -> finishAd(event.ad, completed = event.completed)
            is PlayerEvent.AdSkipped -> skipAd(event.ad)
        }
    }

    private suspend fun onPlaying() {
        when (val state = playback) {
            is PlaybackState.Ad -> {
                playback = state.copy(paused = false).resumeWatch(playheadMillis(state))
                return
            }
            else -> Unit
        }
        if (playerIsPlayingAd()) return
        when (val state = playback) {
            is PlaybackState.Content -> {
                state.segment.resumeWatch(playheadMillis(state.segment))
                state.segment.stopReason = null
                state.segment.errorMessage = null
                playback = state.copy(phase = ContentPhase.PLAYING)
            }
            is PlaybackState.Suspended -> resumeContent(state)
            is PlaybackState.Idle -> startContent(state.viewSessionId, state.lastSegment)
            is PlaybackState.Ad -> Unit
        }
    }

    /**
     * Opens a play. [previousSegment] is the last play of the same stream session, whose play
     * time this one continues from.
     */
    private suspend fun startContent(
        viewSessionId: String?,
        previousSegment: StreamSession? = null,
    ) {
        val id = viewSessionId ?: newViewSessionId()
        val snapshot = snapshot() ?: return
        val playId = UUID.randomUUID().toString()
        val segment =
            StreamSession(
                streamSessionId = id,
                startedInsertId = UUID.randomUUID().toString(),
                stoppedInsertId = UUID.randomUUID().toString(),
                playId = playId,
                startTimeMillis = snapshot.positionMillis,
                options = options,
                mediaType = snapshot.mediaType,
                snapshot = snapshot,
                playTimeBeforeMillis =
                    previousSegment
                        ?.takeIf { it.streamSessionId == id }
                        ?.durationMillis()
                        ?: 0L,
            ).also { it.resumeWatch(snapshot.positionMillis) }
        streamTracker.trackStreamStarted(
            options = segment.options,
            snapshot = snapshot,
            mediaType = segment.mediaType,
            streamSessionId = id,
            playId = segment.playId,
            startTimeMillis = segment.startTimeMillis,
            timestamp = time.nowMillis(),
            insertId = segment.startedInsertId,
        )
        playback =
            PlaybackState.Content(
                viewSessionId = id,
                segment = segment,
                heartbeat = createHeartbeat(segment),
                phase = ContentPhase.PLAYING,
            )
    }

    private suspend fun onPaused() {
        when (val state = playback) {
            is PlaybackState.Content -> {
                finishSegment(state.segment, state.heartbeat, StopReason.PAUSED)
                playback = PlaybackState.Idle(state.viewSessionId, state.segment)
            }
            is PlaybackState.Suspended -> {
                finishSegment(state.segment, heartbeat = null, reason = StopReason.PAUSED)
                playback = PlaybackState.Idle(state.viewSessionId, state.segment)
            }
            is PlaybackState.Ad -> {
                playback = state.pauseWatch(playheadMillis(state)).copy(paused = true)
            }
            is PlaybackState.Idle -> Unit
        }
    }

    private suspend fun onBuffering() {
        when (val state = playback) {
            is PlaybackState.Content -> {
                state.segment.pauseWatch(playheadMillis(state.segment))
                playback = state.copy(phase = ContentPhase.WAITING)
            }
            is PlaybackState.Suspended -> {
                state.segment.pauseWatch(playheadMillis(state.segment))
            }
            is PlaybackState.Ad -> {
                playback = state.pauseWatch(playheadMillis(state))
            }
            is PlaybackState.Idle -> Unit
        }
    }

    private suspend fun onSeeking(event: PlayerEvent.Seeking) {
        val destination = snapshot()?.positionMillis ?: event.previousSnapshot.positionMillis
        when (val state = playback) {
            is PlaybackState.Content -> {
                state.segment.pauseWatch(event.previousSnapshot.positionMillis)
                state.segment.noteSeek(destination)
                if (playerIsPlaying() && !playerIsPlayingAd()) {
                    state.segment.resumeWatch(destination)
                    playback = state.copy(phase = ContentPhase.PLAYING)
                } else {
                    playback = state.copy(phase = ContentPhase.WAITING)
                }
            }
            is PlaybackState.Suspended -> {
                state.segment.pauseWatch(event.previousSnapshot.positionMillis)
                state.segment.noteSeek(destination)
            }
            is PlaybackState.Ad -> {
                val accrued =
                    event.previousAdPositionMillis?.let { state.pauseWatch(it) } ?: state
                val stillPlaying = playerIsPlaying() && !state.paused
                playback =
                    if (accrued.lastPlayheadMillis == null && stillPlaying) {
                        accrued.resumeWatch(adPlayheadMillis())
                    } else if (event.previousAdPositionMillis == null) {
                        state.noteSeek(adPlayheadMillis())
                    } else {
                        accrued
                    }
            }
            is PlaybackState.Idle -> {
                if (playerIsPlaying() && !playerIsPlayingAd()) {
                    startContent(state.viewSessionId, state.lastSegment)
                }
            }
        }
    }

    private suspend fun onReady() {
        when (val state = playback) {
            is PlaybackState.Content -> {
                if (state.phase == ContentPhase.PLAYING) return
                state.segment.stopReason = null
                state.segment.resumeWatch(playheadMillis(state.segment))
                playback = state.copy(phase = ContentPhase.PLAYING)
            }
            is PlaybackState.Suspended -> {
                state.segment.stopReason = null
                playback = state.copy(phase = ContentPhase.PLAYING)
            }
            is PlaybackState.Ad -> {
                if (!state.paused) {
                    playback = state.resumeWatch(playheadMillis(state))
                }
            }
            is PlaybackState.Idle -> {
                if (!playerIsPlaying() || playerIsPlayingAd()) return
                startContent(state.viewSessionId, state.lastSegment)
            }
        }
    }

    private suspend fun finishSession(
        reason: StopReason?,
        errorMessage: String? = null,
    ) {
        finishPlayback(reason, errorMessage)
        playback = PlaybackState.Idle()
    }

    private fun freezeCurrentSegment(snapshot: PlayerMediaSnapshot) {
        when (val state = playback) {
            is PlaybackState.Content -> state.segment.freeze(snapshot)
            is PlaybackState.Suspended -> state.segment.freeze(snapshot)
            is PlaybackState.Ad -> state.content?.segment?.freeze(snapshot)
            is PlaybackState.Idle -> Unit
        }
    }

    private suspend fun onAdStarted(ad: AdContext) {
        val current = playback
        if (current is PlaybackState.Ad) {
            finishAdPlayback(current, AdCompletionStatus.ABANDONED)
        }
        val content: PlaybackState.Suspended?
        val lastSegment: StreamSession?
        when (val state = playback) {
                is PlaybackState.Content -> {
                    state.segment.pauseWatch(playheadMillis(state.segment))
                    state.heartbeat.cancel()
                content =
                    PlaybackState.Suspended(
                        viewSessionId = state.viewSessionId,
                        segment = state.segment,
                        phase = state.phase,
                    )
                lastSegment = null
            }
            is PlaybackState.Suspended -> {
                content = state
                lastSegment = null
            }
            is PlaybackState.Ad -> {
                content = state.content
                lastSegment = state.lastSegment
            }
            is PlaybackState.Idle -> {
                content = null
                lastSegment = state.lastSegment
            }
        }
        val id = playback.viewSessionId ?: newViewSessionId()
        val playing = playerIsPlaying()
        val heartbeat = createAdHeartbeat()
        playback =
            PlaybackState.Ad(
                viewSessionId = id,
                ad = ad,
                lastPlayheadMillis = if (playing) adPlayheadMillis() else null,
                content = content,
                lastSegment = lastSegment,
                paused = !playing,
                heartbeat = heartbeat,
                stoppedInsertId = UUID.randomUUID().toString(),
            )
        streamTracker.trackAdStarted(
            options = options,
            ad = ad,
            streamSessionId = id,
            timestamp = time.nowMillis(),
            insertId = UUID.randomUUID().toString(),
        )
        heartbeat.start()
    }

    private suspend fun finishAd(
        ad: AdContext,
        completed: Boolean,
    ) {
        val state = playback as? PlaybackState.Ad ?: return
        val status =
            if (completed) AdCompletionStatus.ENDED else AdCompletionStatus.ABANDONED
        val finished = finishAdPlayback(state, status, endPositionMillis = ad.positionMillis)
        continueAfterAd(finished)
    }

    private suspend fun skipAd(ad: AdContext) {
        val state = playback as? PlaybackState.Ad ?: return
        streamTracker.trackAdSkipped(
            options = options,
            ad = ad,
            streamSessionId = state.viewSessionId,
            timestamp = time.nowMillis(),
            insertId = UUID.randomUUID().toString(),
        )
        val finished = finishAdPlayback(state, AdCompletionStatus.SKIPPED, endPositionMillis = ad.positionMillis)
        continueAfterAd(finished)
    }

    /**
     * [endPositionMillis] is the ad playhead captured when the ad ended. Media3 has already
     * moved [Player.getCurrentPosition] back to the content resume point by the time this runs.
     */
    private suspend fun finishAdPlayback(
        state: PlaybackState.Ad,
        status: AdCompletionStatus,
        endPositionMillis: Long? = null,
    ): PlaybackState.Ad {
        val playhead = endPositionMillis ?: playheadMillis(state)
        val finished =
            state.pauseWatch(playhead).copy(
                completionStatus = status,
                ad = state.ad.copy(positionMillis = playhead),
            )
        playback = finished
        runCatchingCancellable {
            finished.heartbeat.stop()
        }
        return finished
    }

    private suspend fun continueAfterAd(state: PlaybackState.Ad) {
        val content = state.content
        val playingContent = playerIsPlaying() && !playerIsPlayingAd()
        if (content == null) {
            playback = PlaybackState.Idle(state.viewSessionId, state.lastSegment)
            if (playingContent) startContent(state.viewSessionId, state.lastSegment)
            return
        }
        if (state.paused) {
            finishSegment(content.segment, heartbeat = null, reason = StopReason.PAUSED)
            playback = PlaybackState.Idle(state.viewSessionId, content.segment)
            return
        }
        playback = content
        if (playingContent) resumeContent(content)
    }

    private suspend fun resumeContent(state: PlaybackState.Suspended) {
        state.segment.resumeWatch(playheadMillis(state.segment))
        state.segment.stopReason = null
        state.segment.errorMessage = null
        playback =
            PlaybackState.Content(
                viewSessionId = state.viewSessionId,
                segment = state.segment,
                heartbeat = createHeartbeat(state.segment),
                phase = ContentPhase.PLAYING,
            )
    }

    private fun createHeartbeat(segment: StreamSession): Heartbeat =
        heartbeatFactory
            .create(
                scope = scope,
                stoppedEvent = { timestamp, isFinal ->
                    sendStreamStopped(
                        segment = segment,
                        timestamp = timestamp,
                        stopReason = if (isFinal) segment.stopReason else StopReason.TIMEOUT,
                    )
                },
            ).also { it.start() }

    private fun createAdHeartbeat(): Heartbeat =
        heartbeatFactory.create(
            scope = scope,
            stoppedEvent = stoppedEvent@{ timestamp, isFinal ->
                val state = playback as? PlaybackState.Ad ?: return@stoppedEvent
                sendAdStopped(state = state, timestamp = timestamp, isFinal = isFinal)
            },
        )

    private suspend fun sendAdStopped(
        state: PlaybackState.Ad,
        timestamp: Long,
        isFinal: Boolean,
    ) {
        val playhead = playheadMillis(state)
        val current =
            (playback as? PlaybackState.Ad)?.takeIf { it.stoppedInsertId == state.stoppedInsertId }
                ?: return
        val accrued =
            if (!isFinal && current.lastPlayheadMillis != null) {
                current.resumeWatch(playhead).also { updated ->
                    val latest = playback as? PlaybackState.Ad
                    if (latest?.stoppedInsertId == current.stoppedInsertId && latest.lastPlayheadMillis != null) {
                        playback = updated
                    }
                }
            } else {
                current
            }
        val status = if (isFinal) accrued.completionStatus else AdCompletionStatus.TIMEOUT
        streamTracker.trackAdStopped(
            options = options,
            ad = accrued.ad,
            streamSessionId = accrued.viewSessionId,
            playTimeMillis = accrued.durationMillis(playhead),
            status = status,
            timestamp = timestamp,
            insertId = accrued.stoppedInsertId,
        )
    }

    private suspend fun finishPlayback(
        reason: StopReason?,
        errorMessage: String? = null,
        rewriteIdleLastSegment: Boolean = true,
    ) {
        when (val state = playback) {
            is PlaybackState.Content -> {
                finishSegment(state.segment, state.heartbeat, reason, errorMessage)
            }
            is PlaybackState.Suspended -> {
                finishSegment(
                    segment = state.segment,
                    heartbeat = null,
                    reason = reason,
                    errorMessage = errorMessage,
                )
            }
            is PlaybackState.Ad -> {
                finishAdPlayback(state, AdCompletionStatus.ABANDONED)
                state.content?.let {
                    finishSegment(
                        segment = it.segment,
                        heartbeat = null,
                        reason = reason,
                        errorMessage = errorMessage,
                    )
                }
            }
            is PlaybackState.Idle -> {
                if (reason == StopReason.UNTRACKED && rewriteIdleLastSegment) {
                    state.lastSegment?.let {
                        it.stopReason = reason
                        it.errorMessage = errorMessage
                        sendStreamStopped(it, time.nowMillis())
                    }
                }
            }
        }
    }

    private suspend fun finishSegment(
        segment: StreamSession,
        heartbeat: Heartbeat?,
        reason: StopReason?,
        errorMessage: String? = null,
    ) {
        segment.stopReason = reason
        segment.errorMessage = errorMessage
        freezeSegment(segment)
        if (heartbeat == null) {
            sendFinalStreamStopped(segment)
        } else {
            runCatchingCancellable {
                heartbeat.stop()
            }
        }
    }

    private suspend fun sendFinalStreamStopped(segment: StreamSession) {
        runCatchingCancellable {
            sendStreamStopped(segment, time.nowMillis())
        }
    }

    private suspend fun freezeSegment(segment: StreamSession) {
        segment.freeze(snapshot() ?: segment.snapshot)
    }

    private suspend fun sendStreamStopped(
        segment: StreamSession,
        timestamp: Long,
        stopReason: StopReason? = segment.stopReason,
    ) {
        val snapshot =
            if (segment.frozen || isOrphaned()) {
                if (!segment.frozen) segment.freeze(segment.snapshot)
                segment.snapshot
            } else {
                snapshot()?.also { segment.updateSnapshot(it) } ?: segment.snapshot
            }
        streamTracker.trackStreamStopped(
            options = segment.options,
            snapshot = snapshot,
            mediaType = segment.mediaType,
            streamSessionId = segment.streamSessionId,
            playId = segment.playId,
            startTimeMillis = segment.startTimeMillis,
            playTimeMillis = segment.durationMillis(),
            timestamp = timestamp,
            insertId = segment.stoppedInsertId,
            stopReason = stopReason,
            errorMessage = segment.errorMessage,
        )
    }

    private suspend fun snapshot(): PlayerMediaSnapshot? =
        runCatchingCancellable { observer.snapshot() }.getOrNull()

    private suspend fun playheadMillis(segment: StreamSession): Long =
        snapshot()?.positionMillis ?: segment.snapshot.positionMillis

    private suspend fun playheadMillis(state: PlaybackState.Ad): Long =
        withContext(playerDispatcher) {
            val player = playerReference.get()
            if (player != null && player.isCurrentAd(state.ad)) {
                player.currentPosition.coerceAtLeast(0L)
            } else {
                state.lastPlayheadMillis ?: state.ad.positionMillis
            }
        }

    /**
     * Ad playhead. Media3 keeps [Player.getContentPosition] at the content resume point during
     * ads; [Player.getCurrentPosition] is the position in the ad.
     */
    private suspend fun adPlayheadMillis(): Long =
        withContext(playerDispatcher) {
            val player = playerReference.get()
            if (player?.isPlayingAd == true) player.currentPosition.coerceAtLeast(0L) else 0L
        }

    private suspend fun playerIsPlaying(): Boolean =
        withContext(playerDispatcher) { playerReference.get()?.isPlaying == true }

    @OptIn(UnstableApi::class)
    private fun Player.isCurrentAd(ad: AdContext): Boolean =
        isPlayingAd &&
            currentAdGroupIndex == ad.adGroupIndex &&
            currentAdIndexInAdGroup == ad.adIndexInAdGroup &&
            currentMediaItemIndex == ad.mediaItemIndex

    private suspend fun playerIsPlayingAd(): Boolean =
        withContext(playerDispatcher) { playerReference.get()?.isPlayingAd == true }

    internal fun isBoundTo(player: Player): Boolean = playerReference.get() === player

    internal fun isOrphaned(): Boolean = playerReference.get() == null

    internal fun hasStopped(): Boolean = stopped.get()

    private fun newViewSessionId(): String = UUID.randomUUID().toString()

    private enum class ContentPhase {
        PLAYING,
        WAITING,
    }

    private sealed interface PlaybackState {
        val viewSessionId: String?

        data class Idle(
            override val viewSessionId: String? = null,
            val lastSegment: StreamSession? = null,
        ) : PlaybackState

        data class Content(
            override val viewSessionId: String,
            val segment: StreamSession,
            val heartbeat: Heartbeat,
            val phase: ContentPhase,
        ) : PlaybackState

        data class Suspended(
            override val viewSessionId: String,
            val segment: StreamSession,
            val phase: ContentPhase,
        ) : PlaybackState

        data class Ad(
            override val viewSessionId: String,
            val ad: AdContext,
            val playTimeMillis: Long = 0L,
            val lastPlayheadMillis: Long? = null,
            val content: Suspended?,
            val lastSegment: StreamSession? = null,
            val paused: Boolean = false,
            val heartbeat: Heartbeat,
            val stoppedInsertId: String,
            val completionStatus: AdCompletionStatus = AdCompletionStatus.TIMEOUT,
        ) : PlaybackState {
            fun pauseWatch(positionMillis: Long): Ad {
                val last = lastPlayheadMillis ?: return this
                return copy(
                    playTimeMillis = playTimeMillis + (positionMillis - last).coerceAtLeast(0),
                    lastPlayheadMillis = null,
                )
            }

            fun resumeWatch(positionMillis: Long): Ad {
                val last = lastPlayheadMillis ?: return copy(lastPlayheadMillis = positionMillis)
                return copy(
                    playTimeMillis = playTimeMillis + (positionMillis - last).coerceAtLeast(0),
                    lastPlayheadMillis = positionMillis,
                )
            }

            fun noteSeek(positionMillis: Long): Ad =
                if (lastPlayheadMillis == null) this else copy(lastPlayheadMillis = positionMillis)

            fun durationMillis(positionMillis: Long): Long =
                playTimeMillis +
                    (lastPlayheadMillis?.let { positionMillis - it }?.coerceAtLeast(0) ?: 0)
        }
    }
}
