package com.amplitude.platform.plugins

import com.amplitude.Constants
import com.amplitude.events.BaseEvent
import com.amplitude.platform.Plugin

class ContextPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.Amplitude

    override fun setup(amplitude: com.amplitude.Amplitude) {
        super.setup(amplitude)
    }

    private fun applyContextData(event: BaseEvent) {
        event.library = Constants.SDK_LIBRARY + "/" + Constants.SDK_VERSION
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        applyContextData(event)
        return event
    }
}
