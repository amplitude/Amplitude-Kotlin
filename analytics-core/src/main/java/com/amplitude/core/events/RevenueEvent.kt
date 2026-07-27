package com.amplitude.core.events

import com.amplitude.core.Constants

public open class RevenueEvent : BaseEvent() {
    override var eventType: String = Constants.AMP_REVENUE_EVENT
}
