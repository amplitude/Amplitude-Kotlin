package com.amplitude.android.plugins

import com.amplitude.Amplitude
import com.amplitude.events.BaseEvent
import com.amplitude.platform.Plugin

class AndroidContextPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent? {
        return event
    }
}
