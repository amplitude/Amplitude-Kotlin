package com.amplitude.android.sample

import android.app.Application
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import org.json.JSONObject

class MainApplication : Application() {
    companion object {
        lateinit var amplitude: Amplitude
        const val AMPLITUDE_API_KEY = BuildConfig.AMPLITUDE_API_KEY
    }

    override fun onCreate() {
        super.onCreate()

        // init instance
        amplitude = Amplitude(
            Configuration(
                apiKey = AMPLITUDE_API_KEY,
                context = applicationContext
            )
        )

        // add sample plugin
        amplitude.add(object : Plugin {
            override val type: Plugin.Type = Plugin.Type.Enrichment
            override lateinit var amplitude: com.amplitude.core.Amplitude

            override fun execute(event: BaseEvent): BaseEvent? {
                event.eventProperties = event.eventProperties ?: JSONObject()
                event.eventProperties?.let {
                    it.put("custom android event property", "test")
                }
                return event
            }
        })

        // identify a sample user
        amplitude.setUserId("android-kotlin-sample-user")
    }
}
