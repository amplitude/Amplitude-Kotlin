package com.amplitude.kotlin.platform.plugins

import com.amplitude.kotlin.events.BaseEvent
import com.amplitude.kotlin.events.GroupIdentifyEvent
import com.amplitude.kotlin.events.IdentifyEvent
import com.amplitude.kotlin.events.RevenueEvent
import com.amplitude.kotlin.platform.DestinationPlugin

class AmplitudeDestination : DestinationPlugin() {
    override fun track(payload: BaseEvent): BaseEvent? {
        return payload
    }

    override fun identify(payload: IdentifyEvent): IdentifyEvent? {
        return payload
    }

    override fun groupIdentify(payload: GroupIdentifyEvent): GroupIdentifyEvent? {
        return payload
    }

    override fun revenue(payload: RevenueEvent): RevenueEvent? {
        return payload
    }

    override fun flush() {

    }
}