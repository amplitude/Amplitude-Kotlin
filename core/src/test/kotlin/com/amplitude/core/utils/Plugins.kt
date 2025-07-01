package com.amplitude.core.utils

import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.GroupIdentifyEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.RevenueEvent
import com.amplitude.core.platform.EventPlugin
import com.amplitude.core.platform.Plugin

open class StubPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
}

class TestRunPlugin(
    override val name: String? = null,
    var closure: (BaseEvent?) -> Unit
) : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    var ran = false

    override fun track(payload: BaseEvent): BaseEvent? {
        updateState(true)
        closure(payload)
        return payload
    }

    override fun identify(payload: IdentifyEvent): IdentifyEvent? {
        updateState(true)
        closure(payload)
        return payload
    }

    override fun groupIdentify(payload: GroupIdentifyEvent): GroupIdentifyEvent? {
        updateState(true)
        closure(payload)
        return payload
    }

    override fun revenue(payload: RevenueEvent): RevenueEvent? {
        updateState(true)
        closure(payload)
        return payload
    }

    fun updateState(ran: Boolean) {
        this.ran = ran
    }
}
