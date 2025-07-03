# Amplitude Kotlin Core

This module contains the platform-independent core of the Amplitude Kotlin SDK. It exposes the common analytics pipeline, event handling, storage abstraction and network layer that are consumed by platform specific modules.

## Structure
- `src/main/java/com/amplitude/core` – core analytics implementation
    - `events` – core event definitions
    - `network` – HTTP client and API interaction
    - `platform` – abstractions for platform integrations
    - `utilities` – helper classes used across the SDK
- `src/main/java/com/amplitude/common` – shared utilities for JVM targets
- `src/main/java/com/amplitude/eventbridge` – event serialization helpers
- `src/main/java/com/amplitude/id` – identity management

## Usage

### From Maven Central
Add the dependency in your `build.gradle.kts` (replace `<latest-version>` with the most recent release available on [Maven Central](https://search.maven.org/artifact/com.amplitude/analytics-core)):

```kotlin
implementation("com.amplitude:analytics-core:<latest-version>")
```

### From source
When developing inside this repository you can depend on the module directly:

```kotlin
implementation(project(":core"))
```

To publish to your local Maven repository run:

```
./gradlew publishToMavenLocal
```

## Building
Execute:

```
./gradlew :core:assemble
```