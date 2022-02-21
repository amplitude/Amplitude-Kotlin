package com.amplitude.events

import com.amplitude.Constants

class RevenueEvent : BaseEvent() {
    override var eventType = Constants.AMP_REVENUE_EVENT
}