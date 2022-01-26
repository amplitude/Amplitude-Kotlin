package com.amplitude.kotlin.platform.plugins

import com.amplitude.kotlin.Amplitude
import com.amplitude.kotlin.Constants
import com.amplitude.kotlin.events.BaseEvent
import com.amplitude.kotlin.platform.Plugin

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