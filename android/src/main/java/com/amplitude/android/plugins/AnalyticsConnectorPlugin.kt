package com.amplitude.android.plugins

import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.core.Amplitude
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import java.lang.ClassCastException

internal class AnalyticsConnectorPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    private lateinit var connector: AnalyticsConnector

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        val instanceName = amplitude.configuration.instanceName
        connector = AnalyticsConnector.getInstance(instanceName)

        // set up listener to core package to receive exposure events from Experiment
        connector.eventBridge.setEventReceiver { (eventType, eventProperties, userProperties) ->
            val event = BaseEvent()
            event.eventType = eventType
            event.eventProperties = eventProperties?.toMutableMap()
            event.userProperties = userProperties?.toMutableMap()
            amplitude.track(event)
        }
    }

    override fun execute(event: BaseEvent): BaseEvent? {
        val eventUserProperties: Map<String, Any?>? = event.userProperties
        if (eventUserProperties == null || eventUserProperties.isEmpty() || event.eventType == EXPOSURE_EVENT) {
            return event
        }
        val userProperties: MutableMap<String, Map<String, Any?>> = HashMap()
        for ((key, value) in eventUserProperties) {
            if (value is Map<*, *>) {
                try {
                    userProperties[key] = value as Map<String, Any?>
                } catch (e: ClassCastException) {
                    e.printStackTrace()
                }
            }
        }
        connector.identityStore.editIdentity()
            .updateUserProperties(userProperties)
            .commit()

        return event
    }

    companion object {
        const val EXPOSURE_EVENT = "\$exposure"
    }
}
