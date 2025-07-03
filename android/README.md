# Amplitude Kotlin Android SDK

This module contains the Android implementation of the Amplitude Kotlin SDK. It builds on top of `analytics-core` and adds functionality tailored for Android applications.

## Structure
- `src/main/java/com/amplitude/android` – public API and Android-specific logic
    - `events` – Android event definitions
    - `plugins` – default plugins used by the SDK
    - `storage` – persistence layer on Android
    - `migration` – helpers for upgrading storage versions
    - `utilities` – helper utilities
    - `internal` – internal implementation details

## Usage

### From Maven Central
Add the dependency in your `build.gradle.kts` (replace `<latest-version>` with the current release shown on [Maven Central](https://search.maven.org/artifact/com.amplitude/analytics-android)):

```kotlin
implementation("com.amplitude:analytics-android:<latest-version>")
```

### From source
If you are working with the repository directly you can depend on this module:

```kotlin
implementation(project(":android"))
```

Publish to your local Maven repository with:

```
./gradlew publishToMavenLocal
```

## Building
Run the following to build the library:

```
./gradlew :android:assemble
```