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
                val amplitudeExtra = it.extra?.get("amplitude")
                if (amplitudeExtra != null) {
                    try {
                        val ingestionMetadataMap = (amplitudeExtra as Map<String, Any>).get("ingestionMetadata") as Map<String, String>
                        it.ingestionMetadata = IngestionMetadata.fromMap(ingestionMetadataMap)
                    } finally {
                        pipeline.put(it)
                    }
                }
            } else {
                amplitude.logger.warn("Event is invalid for missing information like userId and deviceId. Dropping event: ${it.eventType}")
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
