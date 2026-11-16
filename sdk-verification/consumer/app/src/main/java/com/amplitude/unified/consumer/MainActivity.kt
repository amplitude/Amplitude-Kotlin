package com.amplitude.unified.consumer

import android.app.Activity
import android.os.Bundle
import com.amplitude.unified.AmplitudeUnified

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val amplitude =
            AmplitudeUnified("consumer-api-key", applicationContext) {
                sessionReplay { sampleRate = 1.0 }
                experiment { deploymentKey = "consumer-deployment-key" }
            }
        amplitude.setUserId("consumer-user")
        amplitude.track("consumer_event")
        amplitude.experiment?.fetch()
        amplitude.experiment?.variant("consumer-flag")
        amplitude.sessionReplay?.shouldRecord()
        amplitude.sessionReplay?.flush()
        amplitude.flush()
        amplitude.reset()
    }
}
