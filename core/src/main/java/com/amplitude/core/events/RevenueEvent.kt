package com.amplitude.core.events

import com.amplitude.core.Constants

class RevenueEvent : BaseEvent() {
    override var eventType = Constants.AMP_REVENUE_EVENT
}