# Android SDK (Kotlin)
A Java example app using the Amplitude Android SDK (Kotlin)

# Usage

### Setup the project
You will need to do the following before running the app.

1. Configure Android SDK path
    * Set `ANDROID_SDK_ROOT` in your environment, or
    * Create a `local.properties` file and set `sdk.dir` to your Android SDK path. See [local.properties.example](local.properties.example).


2. Update [build.gradle](build.gradle) with your Amplitude API key:
    ```kotlin
    ext {
        AMPLITUDE_API_KEY = ""
    }
    ```

### Run the App
Run the application using Android Studio or your favorite IDE.

# Project structure
* README.md - you are here *
* src/main/java/com/amplitude/android/sample
  * [MainApplication.kt](src/main/java/com/amplitude/android/sample/MainApplication.java) - Example Java user app using Amplitude Kotlin SDK. A good place to start.
  * [MainActivity.kt](src/main/java/com/amplitude/android/sample/MainActivity.java) - Example activity that tracks an event
