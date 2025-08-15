package com.amplitude.android.sample

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.DestinationPlugin

class TroubleShootingPlugin : DestinationPlugin() {
    private lateinit var logger: Logger

    override fun setup(amplitude: Amplitude) {
        logger = amplitude.logger
        val apiKey = amplitude.configuration.apiKey
        val serverZone = amplitude.configuration.serverZone
        val serverUrl = amplitude.configuration.serverUrl
        logger.debug("Current Configuration : {\"apiKey\": $apiKey, \"serverZone\": $serverZone, \"serverUrl\": $serverUrl}")
        super.setup(amplitude)
    }

    override fun identify(payload: IdentifyEvent): IdentifyEvent? {
        track(payload)
        return super.identify(payload)
    }

    override fun groupIdentify(payload: GroupIdentifyEvent): GroupIdentifyEvent? {
        track(payload)
        return super.groupIdentify(payload)
    }

    override fun revenue(payload: RevenueEvent): RevenueEvent? {
        track(payload)
        return super.revenue(payload)
    }

    override fun track(payload: BaseEvent): BaseEvent {
        val eventString = buildString {
            appendLine("Event Type: ${payload.eventType}")
            appendLine("- User ID: ${payload.userId ?: "N/A"}")
            appendLine("- Device ID: ${payload.deviceId ?: "N/A"}")
            val properties = payload.userProperties ?: emptyMap()
            properties.forEach {
                appendLine("- - User Property: ${it.key} = ${it.value}")
            }

        }
        logger.debug("Processed event: $eventString")
        return payload
    }
}
