package com.amplitude.android.sample

import com.amplitude.common.Logger
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.DestinationPlugin
import com.google.gson.Gson

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

    override fun track(event: BaseEvent): BaseEvent {
        val gson = Gson()
        val eventJsonStr = gson.toJson(event)
        logger.debug("Processed event: $eventJsonStr")
        return event
    }
}
