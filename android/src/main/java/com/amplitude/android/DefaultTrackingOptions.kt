package com.amplitude.android

@Suppress("DEPRECATION")
@Deprecated("Use AutocaptureOption instead")
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
        @Deprecated("Use AutocaptureOption instead.")
        val ALL =
            DefaultTrackingOptions(
                sessions = true,
                appLifecycles = true,
                deepLinks = true,
                screenViews = true,
            )

        @JvmField
        @Deprecated("Use AutocaptureOption instead.")
        val NONE =
            DefaultTrackingOptions(
                sessions = false,
                appLifecycles = false,
                deepLinks = false,
                screenViews = false,
            )
    }

    var sessions: Boolean
        get() = AutocaptureOption.SESSIONS in autocaptureOptions
        set(value) {
            if (value) {
                autocaptureOptions += AutocaptureOption.SESSIONS
            } else {
                autocaptureOptions -= AutocaptureOption.SESSIONS
            }
        }

    var appLifecycles: Boolean
        get() = AutocaptureOption.APP_LIFECYCLES in autocaptureOptions
        set(value) {
            if (value) {
                autocaptureOptions += AutocaptureOption.APP_LIFECYCLES
            } else {
                autocaptureOptions -= AutocaptureOption.APP_LIFECYCLES
            }
        }

    var deepLinks: Boolean
        get() = AutocaptureOption.DEEP_LINKS in autocaptureOptions
        set(value) {
            if (value) {
                autocaptureOptions += AutocaptureOption.DEEP_LINKS
            } else {
                autocaptureOptions -= AutocaptureOption.DEEP_LINKS
            }
        }

    var screenViews: Boolean
        get() = AutocaptureOption.SCREEN_VIEWS in autocaptureOptions
        set(value) {
            if (value) {
                autocaptureOptions += AutocaptureOption.SCREEN_VIEWS
            } else {
                autocaptureOptions -= AutocaptureOption.SCREEN_VIEWS
            }
        }

    internal var autocaptureOptions: MutableSet<AutocaptureOption> = mutableSetOf()

    internal constructor(autocaptureOptions: MutableSet<AutocaptureOption>) : this() {
        this.autocaptureOptions = autocaptureOptions
    }

    init {
        this.sessions = sessions
        this.appLifecycles = appLifecycles
        this.deepLinks = deepLinks
        this.screenViews = screenViews
    }
}
