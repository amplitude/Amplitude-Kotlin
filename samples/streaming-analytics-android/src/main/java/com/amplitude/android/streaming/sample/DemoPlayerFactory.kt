package com.amplitude.android.streaming.sample

import android.app.Application
import com.amplitude.android.Amplitude
import com.amplitude.android.trackPlayer
import com.amplitude.android.streaming.PlayerContent
import com.amplitude.core.AmplitudePreview
import dev.zacsweers.metro.Inject

@Inject
internal class DemoPlayerFactory(
    private val application: Application,
    private val amplitude: Amplitude,
) {
    @OptIn(AmplitudePreview::class)
    fun create(
        catalog: List<SampleMedia>,
        keepPlayingWhenBackgrounded: Boolean,
    ): DemoPlayer {
        val player =
            DemoPlayer(
                context = application,
                catalog = catalog,
                keepPlayingWhenBackgrounded = keepPlayingWhenBackgrounded,
            )
        amplitude.trackPlayer(player.exoPlayer) { mediaItem ->
            val item = catalog.firstOrNull { it.id == mediaItem?.mediaId } ?: player.currentItem
            PlayerContent(
                contentId = item.id,
                title = item.title,
                contentType = item.contentType,
            )
        }
        return player
    }
}
