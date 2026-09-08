package com.amplitude.android.streaming.sample

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AdViewProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
internal class DemoPlayer(
    context: Context,
    private val catalog: List<SampleMedia>,
    val keepPlayingWhenBackgrounded: Boolean,
) {
    private val appContext = context.applicationContext
    private val adsLoader = ImaAdsLoader.Builder(appContext).build()
    private var adViewProvider: AdViewProvider? = null

    val exoPlayer: ExoPlayer =
        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(appContext)
                    .setLocalAdInsertionComponents(
                        { adsLoader },
                        AdViewProvider { adViewProvider?.adViewGroup },
                    ),
            )
            .build()

    var catalogIndex: Int = 0
        private set

    val currentItem: SampleMedia
        get() = catalog[catalogIndex]

    init {
        adsLoader.setPlayer(exoPlayer)
        applyCatalogItem(0)
    }

    fun attachPlayerView(playerView: PlayerView) {
        adViewProvider = playerView
        playerView.player = exoPlayer
        if (exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
        }
    }

    fun detachPlayerView(playerView: PlayerView) {
        if (adViewProvider === playerView) {
            adViewProvider = null
        }
        playerView.player = null
    }

    fun play() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekToDefaultPosition()
        }
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED) {
            pause()
        } else {
            play()
        }
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
        adsLoader.setPlayer(null)
        adsLoader.release()
        exoPlayer.release()
    }

    private fun applyCatalogItem(index: Int) {
        catalogIndex = Math.floorMod(index, catalog.size)
        val item = catalog[catalogIndex]
        val builder =
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id)
        item.adTagUri?.let { tag ->
            builder.setAdsConfiguration(
                MediaItem.AdsConfiguration.Builder(Uri.parse(tag)).build(),
            )
        }
        exoPlayer.setMediaItem(builder.build())
        // IMA needs the ad view group before prepare, so ad-backed items wait for attachPlayerView.
        if (item.adTagUri == null || adViewProvider != null) {
            exoPlayer.prepare()
        }
    }
}
