package com.amplitude.core.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.eventbridge.Event
import com.amplitude.eventbridge.EventChannel
import com.amplitude.eventbridge.EventReceiver

internal class AnalyticsEventReceiver(val amplitude: Amplitude) : EventReceiver {
    override fun receive(channel: EventChannel, event: Event) {
        amplitude.logger.debug("Receive event from event bridge ${event.eventType}")
        amplitude.track(event.toBaseEvent())
    }
}

internal fun Event.toBaseEvent(): BaseEvent {
    val event = BaseEvent()
    event.eventType = this.eventType
    event.eventProperties = this.eventProperties?.let { it.toMutableMap() }
    event.userProperties = this.userProperties?.let { it.toMutableMap() }
    event.groups = this.groups?.let { it.toMutableMap() }
    event.groupProperties = this.groupProperties?.let { it.toMutableMap() }
    return event
}
