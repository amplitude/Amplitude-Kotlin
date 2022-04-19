package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.toMapObj
import com.amplitude.eventbridge.Event
import com.amplitude.eventbridge.EventBridge
import com.amplitude.eventbridge.EventBridgeContainer
import com.amplitude.eventbridge.EventChannel

internal class IdentityEventSender : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    private lateinit var eventBridge: EventBridge

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        eventBridge =
            EventBridgeContainer.getInstance(amplitude.configuration.instanceName).eventBridge
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        if (event.userProperties != null) {
            eventBridge.sendEvent(EventChannel.IDENTIFY, event.toBridgeEvent())
        }
        return event
    }
}

internal fun BaseEvent.toBridgeEvent(): Event {
    return Event(
        this.eventType,
        this.eventProperties,
        this.userProperties,
        this.groups,
        this.groupProperties
    )
}
