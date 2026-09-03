package com.amplitude.android.streaming.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Snapshot of a [DemoPlayer] that Compose can read. Playback runs on the player's own thread, so
 * every field is refreshed from [Player.Listener] callbacks plus a slow position poll.
 */
internal class DemoPlayerState {
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var hasEnded by mutableStateOf(false)
    var title by mutableStateOf("")
    var errorMessage by mutableStateOf<String?>(null)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(C.TIME_UNSET)

    val progress: Float
        get() =
            if (durationMs <= 0L) {
                0f
            } else {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
}

@Composable
internal fun rememberDemoPlayerState(demoPlayer: DemoPlayer): DemoPlayerState {
    val state = remember(demoPlayer) { DemoPlayerState() }

    DisposableEffect(demoPlayer) {
        fun sync() {
            val player = demoPlayer.exoPlayer
            state.isPlaying = player.isPlaying
            state.isBuffering = player.playbackState == Player.STATE_BUFFERING
            state.hasEnded = player.playbackState == Player.STATE_ENDED
            state.durationMs = player.duration
            state.positionMs = player.currentPosition
            state.title = demoPlayer.currentItem.title
        }

        val listener =
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) = sync()

                override fun onPlayerError(error: PlaybackException) {
                    state.errorMessage = error.errorCodeName
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    state.errorMessage = error?.errorCodeName
                }
            }

        sync()
        demoPlayer.addListener(listener)
        onDispose { demoPlayer.removeListener(listener) }
    }

    LaunchedEffect(demoPlayer) {
        while (true) {
            state.positionMs = demoPlayer.exoPlayer.currentPosition
            state.durationMs = demoPlayer.exoPlayer.duration
            delay(POSITION_POLL_MS)
        }
    }

    return state
}

/**
 * One player card: title, optional video surface, playback status, and controls. Controls wrap so
 * they stay reachable on small screens.
 */
@Composable
internal fun PlayerSection(
    label: String,
    demoPlayer: DemoPlayer,
    showSurface: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberDemoPlayerState(demoPlayer)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(state.statusRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (showSurface) {
                PlayerSurface(
                    demoPlayer = demoPlayer,
                    useController = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(MaterialTheme.shapes.medium),
                )
            }

            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "${formatTime(state.positionMs)} / ${formatTime(state.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { demoPlayer.togglePlayPause() }) {
                    Text(stringResource(if (state.isPlaying) R.string.pause else R.string.play))
                }
                OutlinedButton(onClick = { demoPlayer.seekBy(-SEEK_STEP_MS) }) {
                    Text(stringResource(R.string.seek_back))
                }
                OutlinedButton(onClick = { demoPlayer.seekBy(SEEK_STEP_MS) }) {
                    Text(stringResource(R.string.seek_forward))
                }
                OutlinedButton(onClick = { demoPlayer.swapMedia() }) {
                    Text(stringResource(R.string.swap_media))
                }
            }

            state.errorMessage?.let { error ->
                Text(
                    text = stringResource(R.string.playback_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { demoPlayer.retry() }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
    }
}

/**
 * Media3 [PlayerView] bridged into Compose. Detaching clears the player so a disposed surface never
 * keeps receiving frames — otherwise returning from PiP leaves a black video.
 */
@Composable
internal fun PlayerSurface(
    demoPlayer: DemoPlayer,
    useController: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { view ->
            view.player = demoPlayer.exoPlayer
            view.useController = useController
        },
        onRelease = { view ->
            view.player = null
        },
        modifier = modifier,
    )
}

private fun DemoPlayerState.statusRes(): Int =
    when {
        errorMessage != null -> R.string.status_error
        isBuffering -> R.string.status_buffering
        isPlaying -> R.string.status_playing
        hasEnded -> R.string.status_ended
        else -> R.string.status_paused
    }

private fun formatTime(ms: Long): String {
    if (ms == C.TIME_UNSET || ms < 0L) {
        return "--:--"
    }
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private const val POSITION_POLL_MS = 500L
private const val SEEK_STEP_MS = 10_000L
