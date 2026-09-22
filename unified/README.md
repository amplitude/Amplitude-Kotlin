# Unified Android SDK

The Unified SDK installs Analytics, Session Replay, and Experiment from one entry point. It exposes the full Analytics Android API through `AmplitudeUnified`, plus `sessionReplay` and `experiment` accessors.

## Install

Use `compileSdk = 36` or newer. Session Replay 0.30.0 requires it.

```kotlin
dependencies {
    implementation("com.amplitude:unified-android:<version>")
}
```

The dependency brings in Analytics Android, Session Replay, and Experiment. You do not need separate dependencies for those SDKs.

## Configure

```kotlin
val amplitude = AmplitudeUnified("analytics-api-key", applicationContext) {
    analytics {
        instanceName = "main"
    }
    sessionReplay {
        sampleRate = 1.0
    }
    experiment {
        deploymentKey = "experiment-deployment-key"
    }
}

amplitude.track("app opened")
val variant = amplitude.experiment?.variant("checkout")
```

Session Replay defaults to a `0.0` sample rate, so set a nonzero rate to record sessions. Experiment uses the Analytics API key unless you provide `deploymentKey`. Each blade is enabled by default; set `enabled = false` in its configuration block to omit it. The blade accessors return `null` when a blade is unavailable.

If you already install Session Replay or Experiment plugins yourself, remove those registrations when switching to the wrapper to avoid duplicate clients.
