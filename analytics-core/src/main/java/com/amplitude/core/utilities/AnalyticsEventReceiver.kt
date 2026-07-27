package com.amplitude.core.utilities

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.eventbridge.Event
import com.amplitude.eventbridge.EventChannel
import com.amplitude.eventbridge.EventReceiver
import java.lang.ref.WeakReference

internal class AnalyticsEventReceiver(amplitude: Amplitude) : EventReceiver {
    private val amplitudeRef = WeakReference(amplitude)

    override fun receive(
        channel: EventChannel,
        event: Event,
    ) {
        val amplitude = amplitudeRef.get() ?: return
        amplitude.logger.debug("Receive event from event bridge ${event.eventType}")
        amplitude.track(event.toBaseEvent())
    }
}

internal fun Event.toBaseEvent(): BaseEvent {
    val event = BaseEvent()
    event.eventType = this.eventType
    event.eventProperties = this.eventProperties?.deepCopy()
    event.userProperties = this.userProperties?.deepCopy()
    event.groups = this.groups?.deepCopy()
    event.groupProperties = this.groupProperties?.deepCopy()
    return event
}
