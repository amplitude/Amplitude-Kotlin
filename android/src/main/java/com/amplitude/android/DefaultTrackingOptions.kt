package com.amplitude.android

open class DefaultTrackingOptions
    @JvmOverloads
    constructor(
        var sessions: Boolean = true,
        var appLifecycles: Boolean = false,
        var deepLinks: Boolean = false,
        var screenViews: Boolean = false,
    ) {
        var userInteractions = false
            @ExperimentalAmplitudeFeature
            set

        // Prebuilt options for easier usage
        companion object {
            @JvmField
            val ALL =
                DefaultTrackingOptions(
                    sessions = true,
                    appLifecycles = true,
                    deepLinks = true,
                    screenViews = true,
                )

            @JvmField
            val NONE =
                DefaultTrackingOptions(
                    sessions = false,
                    appLifecycles = false,
                    deepLinks = false,
                    screenViews = false,
                )
        }

        @ExperimentalAmplitudeFeature
        constructor(
            sessions: Boolean = true,
            appLifecycles: Boolean = false,
            deepLinks: Boolean = false,
            screenViews: Boolean = false,
            userInteractions: Boolean = false,
        ) : this(sessions, appLifecycles, deepLinks, screenViews) {
            this.userInteractions = userInteractions
        }
    }
