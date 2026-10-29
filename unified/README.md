# Unified Android SDK

The Unified SDK installs Analytics, Session Replay, and Experiment from one entry point. It exposes the full Analytics Android API through `AmplitudeUnified`, plus `sessionReplay` and `experiment` accessors.

## Install

Use `compileSdk = 35` or newer.

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

## Shared identity

Analytics and Experiment share the identity inherited by `AmplitudeUnified`:

```kotlin
import com.amplitude.core.events.Identify

// Inside a coroutine, wait before reading the persisted device ID.
amplitude.isBuilt.await()
val deviceId = requireNotNull(amplitude.getDeviceId())
amplitude.setIdentity("user-123", deviceId, mapOf("plan" to "paid"))

amplitude.identify(Identify().unset("plan"))
```

`setIdentity` replaces both IDs and user properties together. Properties are process-local and are not persisted. Omitting properties clears the local map. To delete server-side properties, use `Identify().unset("property")` or `Identify().clearAll()`.
