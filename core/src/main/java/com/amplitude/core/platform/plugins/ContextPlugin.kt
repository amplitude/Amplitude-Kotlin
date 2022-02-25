package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin

class ContextPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    override fun setup(amplitude: Amplitude) {
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
