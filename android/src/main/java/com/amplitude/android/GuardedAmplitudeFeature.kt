package com.amplitude.android

@RequiresOptIn(
    message =
        "This feature is guarded and should only be used by Amplitude SDK developers. " +
            "It is not intended for public use and may change without notice.",
)
@Retention(AnnotationRetention.BINARY)
annotation class GuardedAmplitudeFeature
