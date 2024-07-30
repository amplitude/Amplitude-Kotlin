package com.amplitude.android

@Suppress("DEPRECATION")
@Deprecated("Use AutocaptureOptions instead", ReplaceWith("AutocaptureOptions"))
open class DefaultTrackingOptions
@JvmOverloads
constructor(
    sessions: Boolean = true,
    appLifecycles: Boolean = false,
    deepLinks: Boolean = false,
    screenViews: Boolean = false,
) {
    // Prebuilt options for easier usage
    companion object {
        @JvmField
        @Deprecated("Use AutocaptureOptions instead", ReplaceWith("AutocaptureOptions(appLifecycles = true, deepLinks = true, screenViews = true, elementInteractions = true)"))
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

    var sessions: Boolean = sessions
        set(value) {
            field = value
            autocapture?.sessions = value
        }

    var appLifecycles: Boolean = appLifecycles
        set(value) {
            field = value
            autocapture?.appLifecycles = value
        }

    var deepLinks: Boolean = deepLinks
        set(value) {
            field = value
            autocapture?.deepLinks = value
        }

    var screenViews: Boolean = screenViews
        set(value) {
            field = value
            autocapture?.screenViews = value
        }

    private var autocapture: AutocaptureOptions? = null

    internal fun withAutocaptureOptions(autocapture: AutocaptureOptions): DefaultTrackingOptions {
        this.autocapture = autocapture
        return this
    }
}
