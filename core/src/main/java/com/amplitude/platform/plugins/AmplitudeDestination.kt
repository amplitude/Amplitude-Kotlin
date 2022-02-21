package com.amplitude.platform.plugins

import com.amplitude.Amplitude
import com.amplitude.events.BaseEvent
import com.amplitude.events.GroupIdentifyEvent
import com.amplitude.events.IdentifyEvent
import com.amplitude.events.RevenueEvent
import com.amplitude.platform.DestinationPlugin
import com.amplitude.platform.EventPipeline

class AmplitudeDestination : DestinationPlugin() {
    private lateinit var pipeline: EventPipeline

    override fun track(payload: BaseEvent): BaseEvent? {
        enqueue(payload)
        return payload
    }

    override fun identify(payload: IdentifyEvent): IdentifyEvent? {
        enqueue(payload)
        return payload
    }

    override fun groupIdentify(payload: GroupIdentifyEvent): GroupIdentifyEvent? {
        enqueue(payload)
        return payload
    }

    override fun revenue(payload: RevenueEvent): RevenueEvent? {
        enqueue(payload)
        return payload
    }

    override fun flush() {
        pipeline.flush()
    }

    private fun enqueue(payload: BaseEvent?) {
        payload?.let {
            pipeline.put(it)
        }
    }

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)

        with(amplitude) {
            pipeline = EventPipeline(
                amplitude
            )
            pipeline.start()
        }
    }
}