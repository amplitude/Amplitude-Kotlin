package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.DestinationPlugin
import com.amplitude.core.platform.EventPipeline

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
            if (it.isValid()) {
                pipeline.put(it)
            } else {
                amplitude.logger.warn("Event is invalid. Dropping event: ${it.eventType}")
            }
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
        add(IdentityEventSender())
    }
}
