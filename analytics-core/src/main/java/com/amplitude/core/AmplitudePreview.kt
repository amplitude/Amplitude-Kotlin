package com.amplitude.core

/**
 * Marks APIs that are available for early adoption but not yet stable.
 *
 * Preview APIs may change in a minor release. Prefer them only when you can absorb
 * breaking changes, and migrate off once the feature is promoted to the public surface.
 */
@RequiresOptIn(
    message =
        "This Amplitude API is in preview and may change without notice. " +
            "Opt in only if you can absorb breaking changes.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class AmplitudePreview
