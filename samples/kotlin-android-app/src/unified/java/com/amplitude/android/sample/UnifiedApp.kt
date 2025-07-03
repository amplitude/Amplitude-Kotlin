package com.amplitude.android.sample

import com.amplitude.android.Configuration
import com.amplitude.android.autocaptureOptions
import com.amplitude.android.unified.Amplitude

class UnifiedApp : MainApplication() {
    override fun initAmplitudeLibrary() {
        val httpClient = CustomOkHttpClient()
        val configuration =
            Configuration(
                apiKey = AMPLITUDE_API_KEY,
                context = applicationContext,
                autocapture =
                    autocaptureOptions {
                        +sessions
                        +appLifecycles
                        +deepLinks
                        +screenViews
                        +elementInteractions
                    },
                httpClient = httpClient,
                minTimeBetweenSessionsMillis = 60_000L,
            )
        httpClient.initialize(configuration)
        amplitude = Amplitude(configuration)
    }
}