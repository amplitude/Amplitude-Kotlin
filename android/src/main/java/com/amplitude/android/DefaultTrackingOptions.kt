package com.amplitude.android

@Suppress("DEPRECATION")
@Deprecated("Use AutocaptureOptions instead", ReplaceWith("AutocaptureOptions"))
open class DefaultTrackingOptions
@JvmOverloads
constructor(
    var sessions: Boolean = true,
    var appLifecycles: Boolean = false,
    var deepLinks: Boolean = false,
    var screenViews: Boolean = false,
) {
    // Prebuilt options for easier usage
    companion object {
        @JvmField
        @Deprecated("Use AutocaptureOptions instead", ReplaceWith("AutocaptureOptions(appLifecycles = true, deepLinks = true, screenViews = true)"))
        val ALL =
            DefaultTrackingOptions(
                sessions = true,
                appLifecycles = true,
                deepLinks = true,
                screenViews = true,
            )

        @JvmField
        @Deprecated("Use AutocaptureOptions instead", ReplaceWith("AutocaptureOptions(sessions = false)"))
        val NONE =
            DefaultTrackingOptions(
                sessions = false,
                appLifecycles = false,
                deepLinks = false,
                screenViews = false,
            )
    }
}
