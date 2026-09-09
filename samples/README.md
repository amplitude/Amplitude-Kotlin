# Sample Projects

This directory contains sample applications that demonstrate how to integrate the Amplitude Kotlin SDK.

## Modules
- `kotlin-android-app` – minimal Android application demonstrating `analytics-android` integration. See the module's README for details on configuring API keys and running the app.
- `streaming-analytics-android` – Media3 sample with XML and Compose `PlayerView`s (concurrent players). See the module README.

## Running the sample

To install the debug build on a connected device run:

```
./gradlew :samples:kotlin-android-app:installDebug
./gradlew :samples:streaming-app:installDebug
```

You may also open the project in Android Studio for easier exploration. Refer to each sample's `README.md` for setup instructions.