package com.amplitude.platform.plugins

import com.amplitude.events.BaseEvent
import com.amplitude.events.GroupIdentifyEvent
import com.amplitude.events.IdentifyEvent
import com.amplitude.events.RevenueEvent
import com.amplitude.platform.DestinationPlugin

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