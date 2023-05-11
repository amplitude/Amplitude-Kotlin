package com.amplitude.android.sample

import android.app.Application
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.experiment.Experiment
import com.amplitude.experiment.ExperimentConfig
import java.lang.Exception

class MainApplication : Application() {
    companion object {
        lateinit var amplitude: Amplitude
        const val AMPLITUDE_API_KEY = BuildConfig.AMPLITUDE_API_KEY
        const val EXPERIMENT_API_KEY = BuildConfig.EXPERIMENT_API_KEY
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

        // Sample for Experiment Integration
        val experimentConfig = ExperimentConfig.builder()
            .debug(true)
            .build()

        val experimentClient = Experiment.initializeWithAmplitudeAnalytics(this, EXPERIMENT_API_KEY, experimentConfig)

        try {
            experimentClient.fetch(null).get()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // add sample plugin
        amplitude.add(object : Plugin {
            override val type: Plugin.Type = Plugin.Type.Enrichment
            override lateinit var amplitude: com.amplitude.core.Amplitude

            override fun execute(event: BaseEvent): BaseEvent? {
                event.eventProperties = event.eventProperties ?: mutableMapOf()
                event.eventProperties?.put("custom android event property", "test")
                return event
            }
        })

        // identify a sample user
        amplitude.setUserId("android-kotlin-sample-user")
    }
}
