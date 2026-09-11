package com.amplitude.android.streaming.sample

import com.amplitude.android.streaming.PlayerContent
import com.amplitude.core.AmplitudePreview

@OptIn(AmplitudePreview::class)
internal data class SampleMedia(
    val id: String,
    val title: String,
    val deliveryMode: String,
    val uri: String,
) {
    companion object {
        val vod: List<SampleMedia> =
            listOf(
                SampleMedia(
                    id = "big-buck-bunny",
                    title = "Big Buck Bunny (10 min)",
                    deliveryMode = PlayerContent.DELIVERY_MODE_ON_DEMAND,
                    uri = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
                ),
                SampleMedia(
                    id = "frame-counter",
                    title = "Frame counter (1 hour)",
                    deliveryMode = PlayerContent.DELIVERY_MODE_ON_DEMAND,
                    uri = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/frame-counter-one-hour.mp4",
                ),
            )

        val audio: List<SampleMedia> =
            listOf(
                SampleMedia(
                    id = "jazz-in-paris",
                    title = "Jazz in Paris",
                    deliveryMode = PlayerContent.DELIVERY_MODE_ON_DEMAND,
                    uri = "https://storage.googleapis.com/exoplayer-test-media-0/Jazz_In_Paris.mp3",
                ),
                SampleMedia(
                    id = "play-mp3",
                    title = "ExoPlayer test tone",
                    deliveryMode = PlayerContent.DELIVERY_MODE_ON_DEMAND,
                    uri = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3",
                ),
            )
    }
}
