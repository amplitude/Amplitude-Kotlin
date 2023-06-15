package com.amplitude.android

class DefaultTrackingOptions(
    var trackingSessionEvents: Boolean = true,
    var trackingAppLifecycleEvents: Boolean = false,
    var trackingDeepLinks: Boolean = false,
    var trackingScreenViews: Boolean = false
) {
    // Prebuilt options for easier usage
    companion object {
        val ALL = DefaultTrackingOptions(
            trackingSessionEvents = true,
            trackingAppLifecycleEvents = true,
            trackingDeepLinks = true,
            trackingScreenViews = true
        )
        val NONE = DefaultTrackingOptions(
            trackingSessionEvents = false,
            trackingAppLifecycleEvents = false,
            trackingDeepLinks = false,
            trackingScreenViews = false
        )
    }
}
