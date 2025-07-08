package com.amplitude.android

@RequiresOptIn(
    message =
        "This feature is internal to the Amplitude SDK and is not intended for public use. Use at your own risk",
)
@Retention(AnnotationRetention.BINARY)
annotation class InternalAmplitudeFeature
