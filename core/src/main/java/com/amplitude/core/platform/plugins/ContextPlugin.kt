package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import java.util.UUID

class ContextPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
    }

    private fun applyContextData(event: BaseEvent) {
        event.timestamp ?: let {
            event.timestamp = System.currentTimeMillis()
        }
        event.insertId ?: let {
            event.insertId = UUID.randomUUID().toString()
        }
        event.library ?: let {
            event.library = "${Constants.SDK_LIBRARY}/${Constants.SDK_VERSION}"
        }
        event.userId ?: let {
            event.userId = amplitude.store.userId
        }
        event.deviceId ?: let {
            event.deviceId = amplitude.store.deviceId
        }
        event.partnerId ?: let {
            amplitude.configuration.partnerId ?. let {
                event.partnerId = it
            }
        }
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        applyContextData(event)
        return event
    }
}
