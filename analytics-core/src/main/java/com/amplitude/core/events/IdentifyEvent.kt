package com.amplitude.core.events

import com.amplitude.core.Constants

public open class IdentifyEvent : BaseEvent() {
    override var eventType: String = Constants.IDENTIFY_EVENT
}
