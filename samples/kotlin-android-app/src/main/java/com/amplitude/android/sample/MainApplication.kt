package com.amplitude.android.sample

import android.app.Application
import android.util.Log
import com.amplitude.android.Amplitude
import com.amplitude.android.AutocaptureOption.Companion.ALL
import com.amplitude.android.DeadClickOptions
import com.amplitude.android.InteractionsOptions
import com.amplitude.android.RageClickOptions
import com.amplitude.common.Logger
import com.amplitude.core.Constants
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.Plugin
import com.amplitude.experiment.Experiment
import com.amplitude.experiment.ExperimentConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainApplication : Application() {
    companion object {
        lateinit var amplitude: Amplitude
        const val AMPLITUDE_API_KEY = BuildConfig.AMPLITUDE_API_KEY
        const val EXPERIMENT_API_KEY = BuildConfig.EXPERIMENT_API_KEY
        private const val TAG = "CME-Repro"

        /**
         * Fires events rapidly from IO dispatcher to reproduce the CME race condition.
         * Mirrors the customer's setup: events tracked from Dispatchers.IO,
         * FakeEngagementPlugin serializes on Main, FakeAdIdPlugin modifies on pipeline thread.
         */
        fun fireEventsFromIO(count: Int = 50) {
            CoroutineScope(Dispatchers.IO).launch {
                Log.w(TAG, "Firing $count events from IO dispatcher...")
                repeat(count) { i ->
                    val props = (1..20).associate { "prop_$it" to "value_$it" }.toMutableMap<String, Any?>()
                    amplitude.track(
                        "cme_repro_event_$i",
                        props,
                    )
                }
                Log.w(TAG, "Done firing events")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        amplitude =
            Amplitude(AMPLITUDE_API_KEY, applicationContext) {
                autocapture = ALL
                httpClient =
                    CustomOkHttpClient(
                        apiKey = AMPLITUDE_API_KEY,
                        apiHost = Constants.DEFAULT_API_HOST,
                    )
                interactionsOptions =
                    InteractionsOptions(
                        RageClickOptions(enabled = true),
                        // requires session replay to be enabled
                        DeadClickOptions(enabled = false),
                    )
            }

        // Sample for Experiment Integration
        val experimentConfig =
            ExperimentConfig.builder()
                .debug(true)
                .build()

        val experimentClient = Experiment.initializeWithAmplitudeAnalytics(this, EXPERIMENT_API_KEY, experimentConfig)

        try {
            experimentClient.fetch(null).get()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // set app to debug mode
        amplitude.logger.logMode = Logger.LogMode.DEBUG

        // --- AMP-150851 CME reproduction ---
        // Mirrors the customer's (AJIO) exact plugin order:
        //   1. Engagement plugin (dispatches serialization to Main)
        //   2. Custom ad-ID plugin (modifies userProperties on pipeline thread)
        //
        // Toggle between FakeEngagementPlugin (buggy) and FakeEngagementPluginFixed (fixed)
        // to validate the fix. The fixed version serializes on the pipeline thread before
        // dispatching to Main, so no mutable state crosses the thread boundary.
        amplitude.add(FakeEngagementPluginFixed()) // swap to FakeEngagementPlugin() to reproduce crash
        amplitude.add(FakeAdIdPlugin())

        // add sample plugin
        amplitude.add(
            object : Plugin {
                override val type: Plugin.Type = Plugin.Type.Enrichment
                override lateinit var amplitude: com.amplitude.core.Amplitude

                override fun execute(event: BaseEvent): BaseEvent {
                    event.eventProperties = event.eventProperties ?: mutableMapOf()
                    event.eventProperties?.put("custom android event property", "test")
                    return event
                }
            },
        )

        // add the troubleshooting plugin for debugging
        amplitude.add(TroubleShootingPlugin())

        // identify a sample user
        amplitude.setUserId("android-kotlin-sample-user")
    }
}
