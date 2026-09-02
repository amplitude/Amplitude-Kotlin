package com.amplitude.android.streaming.sample

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject

@Inject
internal class XmlPlayerViewModel(
    playerFactory: DemoPlayerFactory,
) : ViewModel() {
    val player: DemoPlayer =
        playerFactory.create(
            catalog = SampleMedia.vod,
            keepPlayingWhenBackgrounded = true,
        )

    fun onHostStopped(
        isInPip: Boolean,
        isFinishing: Boolean,
    ) {
        player.onHostStopped(isInPip, isFinishing)
    }

    override fun onCleared() {
        player.release()
    }
}
