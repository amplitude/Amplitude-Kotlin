package com.amplitude.android.streaming.internal

import com.amplitude.core.events.BaseEvent

internal class DelayedEvent(
    eventType: String,
    timestamp: Long? = null,
    deviceId: String? = null,
    userId: String? = null,
    sessionId: Long? = null,
    eventProperties: MutableMap<String, Any?> = mutableMapOf(),
) : BaseEvent() {
    init {
        this.eventType = eventType
        this.timestamp = timestamp
        this.deviceId = deviceId
        this.userId = userId
        this.sessionId = sessionId
        this.eventProperties = eventProperties
    }
}
