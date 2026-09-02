package com.amplitude.android.streaming.sample

import android.app.Application
import com.amplitude.android.Amplitude
import dev.zacsweers.metro.Inject

@Inject
internal class DemoPlayerFactory(
    private val application: Application,
    private val amplitude: Amplitude,
) {
    fun create(
        catalog: List<SampleMedia>,
        keepPlayingWhenBackgrounded: Boolean,
    ): DemoPlayer =
        DemoPlayer(
            context = application,
            catalog = catalog,
            keepPlayingWhenBackgrounded = keepPlayingWhenBackgrounded,
        )
}
