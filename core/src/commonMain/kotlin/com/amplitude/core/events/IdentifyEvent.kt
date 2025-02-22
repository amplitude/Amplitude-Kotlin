package com.amplitude.core.events

import com.amplitude.core.Constants

open class IdentifyEvent : BaseEvent() {
    override var eventType = Constants.IDENTIFY_EVENT
}
