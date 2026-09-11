package com.amplitude.android.streaming.sample

import com.amplitude.android.streaming.PlayerContent
import com.amplitude.core.AmplitudePreview

@OptIn(AmplitudePreview::class)
internal data class SampleMedia(
    val id: String,
    val title: String,
    val deliveryMode: String,
    val uri: String,
    val adTagUri: String? = null,
    val extraProperties: Map<String, Any?>? = null,
) {
    companion object {
        private const val SKIPPABLE_PREROLL =
            "https://pubads.g.doubleclick.net/gampad/ads?iu=/21775744923/external/single_preroll_skippable&sz=640x480&ciu_szs=300x250%2C728x90&gdfp_req=1&output=vast&unviewed_position_start=1&env=vp&impl=s&correlator="
        private const val VMAP_PRE_MID_POST =
            "https://pubads.g.doubleclick.net/gampad/ads?iu=/21775744923/external/vmap_ad_samples&sz=640x480&cust_params=sample_ar%3Dpremidpost&ciu_szs=300x250&gdfp_req=1&ad_rule=1&output=vmap&unviewed_position_start=1&env=vp&impl=s&cmsid=496&vid=short_onecue&correlator="

        val vod: List<SampleMedia> =
            listOf(
                SampleMedia(
                    id = "big-buck-bunny",
                    title = "Big Buck Bunny · skippable preroll",
                    deliveryMode = PlayerContent.DELIVERY_MODE_ON_DEMAND,
                    uri = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
                    adTagUri = SKIPPABLE_PREROLL,
                    extraProperties = mapOf("ad_campaign" to "ima-skippable-preroll"),
                ),
                SampleMedia(
                    id = "frame-counter",
                    title = "Frame counter · VMAP pre/mid/post",
                    deliveryMode = PlayerContent.DELIVERY_MODE_ON_DEMAND,
                    uri = "https://storage.googleapis.com/exoplayer-test-media-1/mp4/frame-counter-one-hour.mp4",
                    adTagUri = VMAP_PRE_MID_POST,
                    extraProperties = mapOf("ad_campaign" to "ima-vmap-premidpost"),
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
