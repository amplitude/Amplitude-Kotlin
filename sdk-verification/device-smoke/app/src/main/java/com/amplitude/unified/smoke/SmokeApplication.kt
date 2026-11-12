package com.amplitude.unified.smoke

import android.app.Application
import com.amplitude.common.Logger
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.unified.AmplitudeUnified

class SmokeApplication : Application() {
    lateinit var amplitude: AmplitudeUnified
        private set

    override fun onCreate() {
        super.onCreate()
        val mockServer = BuildConfig.MOCK_SERVER_URL
        amplitude =
            AmplitudeUnified(API_KEY, this) {
                analytics {
                    serverUrl = "$mockServer/2/httpapi"
                    flushQueueSize = 1
                    flushIntervalMillis = 1_000
                    enableAutocaptureRemoteConfig = false
                    enableDiagnostics = false
                }
                sessionReplay {
                    sampleRate = 1.0
                    serverUrl = "$mockServer/sessions/v2/track"
                    // Remote config would need a real API key; the local sample rate drives capture.
                    enableRemoteConfig = false
                }
                experiment {
                    deploymentKey = DEPLOYMENT_KEY
                    config =
                        ExperimentConfig.builder()
                            .serverUrl(mockServer)
                            .flagsServerUrl(mockServer)
                            .build()
                }
            }
        amplitude.logger.logMode = Logger.LogMode.DEBUG
    }

    companion object {
        const val API_KEY = "unified-smoke-api-key"
        const val DEPLOYMENT_KEY = "unified-smoke-deployment-key"
    }
}
