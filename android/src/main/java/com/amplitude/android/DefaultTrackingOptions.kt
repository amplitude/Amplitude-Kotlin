package com.amplitude.android

class DefaultTrackingOptions(
    var sessions: Boolean = true,
    var appLifecycles: Boolean = false,
    var deepLinks: Boolean = false,
    var screenViews: Boolean = false
) {
    // Prebuilt options for easier usage
    companion object {
        val ALL = DefaultTrackingOptions(
            sessions = true,
            appLifecycles = true,
            deepLinks = true,
            screenViews = true
        )
        val NONE = DefaultTrackingOptions(
            sessions = false,
            appLifecycles = false,
            deepLinks = false,
            screenViews = false
        )
    }
}
