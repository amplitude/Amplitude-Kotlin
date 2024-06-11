package com.amplitude.android

@RequiresOptIn(
    message =
        "This feature is experimental, and may change or break at any time. Use with caution.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.CONSTRUCTOR)
annotation class ExperimentalAmplitudeFeature
