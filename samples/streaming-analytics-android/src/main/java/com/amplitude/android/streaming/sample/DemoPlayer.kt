package com.amplitude.android.streaming.sample

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

internal class DemoPlayer(
    context: Context,
    private val catalog: List<SampleMedia>,
    val keepPlayingWhenBackgrounded: Boolean,
) {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    var catalogIndex: Int = 0
        private set

    val currentItem: SampleMedia
        get() = catalog[catalogIndex]

    init {
        applyCatalogItem(0)
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
    }

    fun swapMedia() {
        applyCatalogItem(catalogIndex + 1)
        exoPlayer.play()
    }

    fun retry() {
        exoPlayer.prepare()
    }

    fun addListener(listener: Player.Listener) {
        exoPlayer.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        exoPlayer.removeListener(listener)
    }

    fun onHostStopped(
        isInPip: Boolean,
        isFinishing: Boolean,
    ) {
        if (isFinishing) {
            exoPlayer.pause()
            return
        }
        if (keepPlayingWhenBackgrounded || isInPip) {
            return
        }
        exoPlayer.pause()
    }

    fun release() {
        exoPlayer.release()
    }

    private fun applyCatalogItem(index: Int) {
        catalogIndex = Math.floorMod(index, catalog.size)
        val item = catalog[catalogIndex]
        exoPlayer.setMediaItem(
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id)
                .build(),
        )
        exoPlayer.prepare()
    }
}
