package com.amplitude.android.streaming.internal.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
@OptIn(AmplitudePreview::class)
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
            is PlayerEvent.AdStopped -> finishAd(completed = event.completed)
            is PlayerEvent.AdSkipped -> skipAd(event.ad)
        }
    }

    private suspend fun onPlaying() {
        when (val state = playback) {
            is PlaybackState.Ad -> {
                playback = state.copy(paused = false).resumeWatch(time.elapsedRealtime())
                return
            }
            else -> Unit
        }
        if (playerIsPlayingAd()) return
        when (val state = playback) {
            is PlaybackState.Content -> {
                state.segment.resumeWatch()
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
     * Opens a play. [previousSegment] is the last play of the same stream session, whose watch
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
                time = time,
                watchedBeforeMillis =
                    previousSegment
                        ?.takeIf { it.streamSessionId == id }
                        ?.durationMillis()
                        ?: 0L,
            ).also { it.resumeWatch() }
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
                playback = state.pauseWatch(time.elapsedRealtime()).copy(paused = true)
            }
            is PlaybackState.Idle -> Unit
        }
    }

    private suspend fun onBuffering() {
        interruptContent(StopReason.WAITING)
    }

    private suspend fun onSeeking(event: PlayerEvent.Seeking) {
        if (playback !is PlaybackState.Ad) {
            freezeCurrentSegment(event.previousSnapshot)
        }
        interruptContent(StopReason.SEEKING)
        val state = playback
        if (state is PlaybackState.Idle && playerIsPlaying() && !playerIsPlayingAd()) {
            startContent(state.viewSessionId, state.lastSegment)
        }
    }

    private suspend fun interruptContent(reason: StopReason) {
        when (val state = playback) {
            is PlaybackState.Content -> {
                if (playerIsPlayingAd()) {
                    state.segment.pauseWatch()
                    state.heartbeat.cancel()
                    playback =
                        PlaybackState.Suspended(
                            viewSessionId = state.viewSessionId,
                            segment = state.segment,
                            phase = state.phase,
                        )
                    return
                }
                finishSegment(state.segment, state.heartbeat, reason)
                playback = PlaybackState.Idle(state.viewSessionId, state.segment)
            }
            is PlaybackState.Suspended -> {
                if (playerIsPlayingAd()) return
                finishSegment(state.segment, heartbeat = null, reason = reason)
                playback = PlaybackState.Idle(state.viewSessionId, state.segment)
            }
            is PlaybackState.Ad -> {
                playback = state.pauseWatch(time.elapsedRealtime())
            }
            is PlaybackState.Idle -> Unit
        }
    }

    private suspend fun onReady() {
        when (val state = playback) {
            is PlaybackState.Content -> {
                if (state.phase == ContentPhase.PLAYING) return
                state.segment.stopReason = null
                state.segment.resumeWatch()
                playback = state.copy(phase = ContentPhase.PLAYING)
            }
            is PlaybackState.Suspended -> {
                state.segment.stopReason = null
                playback = state.copy(phase = ContentPhase.PLAYING)
            }
            is PlaybackState.Ad -> {
                if (!state.paused) {
                    playback = state.resumeWatch(time.elapsedRealtime())
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
                state.segment.pauseWatch()
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
                watchStartedAt = if (playing) time.elapsedRealtime() else null,
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

    private suspend fun finishAd(completed: Boolean) {
        val state = playback as? PlaybackState.Ad ?: return
        val status =
            if (completed) AdCompletionStatus.ENDED else AdCompletionStatus.ABANDONED
        val finished = finishAdPlayback(state, status)
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
        val finished = finishAdPlayback(state, AdCompletionStatus.SKIPPED)
        continueAfterAd(finished)
    }

    private suspend fun finishAdPlayback(
        state: PlaybackState.Ad,
        status: AdCompletionStatus,
    ): PlaybackState.Ad {
        val finished =
            state.pauseWatch(time.elapsedRealtime()).copy(completionStatus = status)
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

    private fun resumeContent(state: PlaybackState.Suspended) {
        state.segment.resumeWatch()
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

    private fun sendAdStopped(
        state: PlaybackState.Ad,
        timestamp: Long,
        isFinal: Boolean,
    ) {
        val status = if (isFinal) state.completionStatus else AdCompletionStatus.TIMEOUT
        streamTracker.trackAdStopped(
            options = options,
            ad = state.ad,
            streamSessionId = state.viewSessionId,
            watchDurationMillis = state.durationMillis(time.elapsedRealtime()),
            status = status,
            timestamp = timestamp,
            insertId = state.stoppedInsertId,
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
        segment.pauseWatch()
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
            watchDurationMillis = segment.durationMillis(),
            timestamp = timestamp,
            insertId = segment.stoppedInsertId,
            stopReason = stopReason,
            errorMessage = segment.errorMessage,
        )
    }

    private suspend fun snapshot(): PlayerMediaSnapshot? =
        runCatchingCancellable { observer.snapshot() }.getOrNull()

    private suspend fun playerIsPlaying(): Boolean =
        withContext(playerDispatcher) { playerReference.get()?.isPlaying == true }

    private suspend fun playerIsPlayingAd(): Boolean =
        withContext(playerDispatcher) { playerReference.get()?.isPlayingAd == true }

    internal fun isBoundTo(player: Player): Boolean = playerReference.get() === player

    internal fun isOrphaned(): Boolean = playerReference.get() == null

    internal fun hasStopped(): Boolean = stopped.get()

    private fun newViewSessionId(): String = UUID.randomUUID().toString()

    private enum class ContentPhase {
        PLAYING,
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
            val watchDurationMillis: Long = 0L,
            val watchStartedAt: Long? = null,
            val content: Suspended?,
            val lastSegment: StreamSession? = null,
            val paused: Boolean = false,
            val heartbeat: Heartbeat,
            val stoppedInsertId: String,
            val completionStatus: AdCompletionStatus = AdCompletionStatus.TIMEOUT,
        ) : PlaybackState {
            fun pauseWatch(now: Long): Ad {
                val startedAt = watchStartedAt ?: return this
                return copy(
                    watchDurationMillis = watchDurationMillis + (now - startedAt).coerceAtLeast(0),
                    watchStartedAt = null,
                )
            }

            fun resumeWatch(now: Long): Ad =
                if (watchStartedAt != null) this else copy(watchStartedAt = now)

            fun durationMillis(now: Long): Long =
                watchDurationMillis + (watchStartedAt?.let { now - it }?.coerceAtLeast(0) ?: 0)
        }
    }
}
