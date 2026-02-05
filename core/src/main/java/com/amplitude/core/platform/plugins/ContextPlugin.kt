package com.amplitude.core.platform.plugins

import com.amplitude.core.Amplitude
import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import java.util.UUID

open class ContextPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude

    override fun execute(event: BaseEvent): BaseEvent? {
        applyContextData(event)
        return event
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
        event.ip ?: let {
            // get the ip in server side if there is no event level ip
            event.ip = "\$remote"
        }
        event.plan ?: let {
            amplitude.configuration.plan ?. let {
                event.plan = it.clone()
            }
        }
        event.ingestionMetadata ?: let {
            amplitude.configuration.ingestionMetadata ?. let {
                event.ingestionMetadata = it.clone()
            }
        }
    }
}
