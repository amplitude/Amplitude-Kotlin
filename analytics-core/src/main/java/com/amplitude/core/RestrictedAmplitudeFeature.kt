package com.amplitude.core

@RequiresOptIn(
    message =
        "This feature is restricted for internal use only. " +
            "It is not intended for public use and may change without notice.",
)
@Retention(AnnotationRetention.BINARY)
annotation class RestrictedAmplitudeFeature
