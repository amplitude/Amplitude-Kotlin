package com.amplitude.android.sample

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin

/**
 * Mimics the customer's custom ad-ID plugin that modifies userProperties
 * during the enrichment phase. When this runs after [FakeEngagementPlugin],
 * it writes to the same maps that the engagement plugin is serializing on Main.
 */
class FakeAdIdPlugin : Plugin {
    override val type = Plugin.Type.Enrichment
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent {
        val props = event.userProperties ?: mutableMapOf()
        props["advertisingId"] = "fake-ad-id-${System.nanoTime()}"
        props["adTrackingEnabled"] = true
        event.userProperties = props
        return event
    }
}
