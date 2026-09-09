package com.amplitude.android.streaming.sample

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.Inject

@Inject
internal class ComposePlayerViewModel(
    playerFactory: DemoPlayerFactory,
) : ViewModel() {
    val videoOne: DemoPlayer =
        playerFactory.create(
            catalog = SampleMedia.vod,
            keepPlayingWhenBackgrounded = true,
        )
    val videoTwo: DemoPlayer =
        playerFactory.create(
            catalog = SampleMedia.vod.reversed(),
            keepPlayingWhenBackgrounded = false,
        )
    val audio: DemoPlayer =
        playerFactory.create(
            catalog = SampleMedia.audio,
            keepPlayingWhenBackgrounded = false,
        )

    fun onHostStopped(
        isInPip: Boolean,
        isFinishing: Boolean,
    ) {
        videoOne.onHostStopped(isInPip, isFinishing)
        videoTwo.onHostStopped(isInPip, isFinishing)
        audio.onHostStopped(isInPip, isFinishing)
    }

    fun onEnterPip() {
        videoTwo.pause()
        audio.pause()
    }

    override fun onCleared() {
        videoOne.release()
        videoTwo.release()
        audio.release()
    }
}
