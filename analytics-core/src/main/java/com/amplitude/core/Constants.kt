package com.amplitude.core

public object Constants {
    public const val SDK_LIBRARY: String = "amplitude-kotlin"
    public const val SDK_VERSION: String = "0.0.1"
    public const val DEFAULT_API_HOST: String = "https://api2.amplitude.com/2/httpapi"
    public const val EU_DEFAULT_API_HOST: String = "https://api.eu.amplitude.com/2/httpapi"
    public const val BATCH_API_HOST: String = "https://api2.amplitude.com/batch"
    public const val EU_BATCH_API_HOST: String = "https://api.eu.amplitude.com/batch"
    public const val IDENTIFY_EVENT: String = "\$identify"
    public const val GROUP_IDENTIFY_EVENT: String = "\$groupidentify"
    public const val AMP_REVENUE_EVENT: String = "revenue_amount"
    public const val MAX_PROPERTY_KEYS: Int = 1024
    public const val MAX_STRING_LENGTH: Int = 1024

    @Deprecated(
        "Moved to com.amplitude.android.Constants.EventTypes — requires the analytics-android dependency",
        ReplaceWith("com.amplitude.android.Constants.EventTypes"),
    )
    public object EventTypes {
        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.APPLICATION_INSTALLED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.APPLICATION_INSTALLED"),
        )
        public const val APPLICATION_INSTALLED: String = "[Amplitude] Application Installed"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.APPLICATION_UPDATED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.APPLICATION_UPDATED"),
        )
        public const val APPLICATION_UPDATED: String = "[Amplitude] Application Updated"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.APPLICATION_OPENED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.APPLICATION_OPENED"),
        )
        public const val APPLICATION_OPENED: String = "[Amplitude] Application Opened"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.APPLICATION_BACKGROUNDED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.APPLICATION_BACKGROUNDED"),
        )
        public const val APPLICATION_BACKGROUNDED: String = "[Amplitude] Application Backgrounded"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.DEEP_LINK_OPENED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.DEEP_LINK_OPENED"),
        )
        public const val DEEP_LINK_OPENED: String = "[Amplitude] Deep Link Opened"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.SCREEN_VIEWED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.SCREEN_VIEWED"),
        )
        public const val SCREEN_VIEWED: String = "[Amplitude] Screen Viewed"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.FRAGMENT_VIEWED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.FRAGMENT_VIEWED"),
        )
        public const val FRAGMENT_VIEWED: String = "[Amplitude] Fragment Viewed"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED"),
        )
        public const val ELEMENT_INTERACTED: String = "[Amplitude] Element Interacted"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.NETWORK_TRACKING — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.NETWORK_TRACKING"),
        )
        public const val NETWORK_TRACKING: String = "[Amplitude] Network Request"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.RAGE_CLICK — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.RAGE_CLICK"),
        )
        public const val RAGE_CLICK: String = "[Amplitude] Rage Click"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.DEAD_CLICK — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.DEAD_CLICK"),
        )
        public const val DEAD_CLICK: String = "[Amplitude] Dead Click"
    }

    @Deprecated(
        "Moved to com.amplitude.android.Constants.EventProperties — requires the analytics-android dependency",
        ReplaceWith("com.amplitude.android.Constants.EventProperties"),
    )
    public object EventProperties {
        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.VERSION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.VERSION"),
        )
        public const val VERSION: String = "[Amplitude] Version"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.BUILD — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.BUILD"),
        )
        public const val BUILD: String = "[Amplitude] Build"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.PREVIOUS_VERSION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.PREVIOUS_VERSION"),
        )
        public const val PREVIOUS_VERSION: String = "[Amplitude] Previous Version"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.PREVIOUS_BUILD — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.PREVIOUS_BUILD"),
        )
        public const val PREVIOUS_BUILD: String = "[Amplitude] Previous Build"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.FROM_BACKGROUND — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.FROM_BACKGROUND"),
        )
        public const val FROM_BACKGROUND: String = "[Amplitude] From Background"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.LINK_URL — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.LINK_URL"),
        )
        public const val LINK_URL: String = "[Amplitude] Link URL"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.LINK_REFERRER — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.LINK_REFERRER"),
        )
        public const val LINK_REFERRER: String = "[Amplitude] Link Referrer"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.SCREEN_NAME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.SCREEN_NAME"),
        )
        public const val SCREEN_NAME: String = "[Amplitude] Screen Name"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.FRAGMENT_CLASS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.FRAGMENT_CLASS"),
        )
        public const val FRAGMENT_CLASS: String = "[Amplitude] Fragment Class"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.FRAGMENT_IDENTIFIER — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.FRAGMENT_IDENTIFIER"),
        )
        public const val FRAGMENT_IDENTIFIER: String = "[Amplitude] Fragment Identifier"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.FRAGMENT_TAG — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.FRAGMENT_TAG"),
        )
        public const val FRAGMENT_TAG: String = "[Amplitude] Fragment Tag"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.ACTION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.ACTION"),
        )
        public const val ACTION: String = "[Amplitude] Action"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_CLASS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_CLASS"),
        )
        public const val TARGET_CLASS: String = "[Amplitude] Target Class"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_RESOURCE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_RESOURCE"),
        )
        public const val TARGET_RESOURCE: String = "[Amplitude] Target Resource"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_TAG — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_TAG"),
        )
        public const val TARGET_TAG: String = "[Amplitude] Target Tag"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_TEXT — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_TEXT"),
        )
        public const val TARGET_TEXT: String = "[Amplitude] Target Text"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_SOURCE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_SOURCE"),
        )
        public const val TARGET_SOURCE: String = "[Amplitude] Target Source"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.HIERARCHY — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.HIERARCHY"),
        )
        public const val HIERARCHY: String = "[Amplitude] Hierarchy"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL"),
        )
        public const val NETWORK_TRACKING_URL: String = "[Amplitude] URL"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_QUERY — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_QUERY"),
        )
        public const val NETWORK_TRACKING_URL_QUERY: String = "[Amplitude] URL Query"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_FRAGMENT — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_FRAGMENT"),
        )
        public const val NETWORK_TRACKING_URL_FRAGMENT: String = "[Amplitude] URL Fragment"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_METHOD — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_METHOD"),
        )
        public const val NETWORK_TRACKING_REQUEST_METHOD: String = "[Amplitude] Request Method"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_STATUS_CODE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_STATUS_CODE"),
        )
        public const val NETWORK_TRACKING_STATUS_CODE: String = "[Amplitude] Status Code"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_ERROR_MESSAGE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_ERROR_MESSAGE"),
        )
        public const val NETWORK_TRACKING_ERROR_MESSAGE: String = "[Amplitude] Error Message"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_START_TIME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_START_TIME"),
        )
        public const val NETWORK_TRACKING_START_TIME: String = "[Amplitude] Start Time"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_COMPLETION_TIME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_COMPLETION_TIME"),
        )
        public const val NETWORK_TRACKING_COMPLETION_TIME: String = "[Amplitude] Completion Time"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_DURATION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_DURATION"),
        )
        public const val NETWORK_TRACKING_DURATION: String = "[Amplitude] Duration"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY_SIZE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY_SIZE"),
        )
        public const val NETWORK_TRACKING_REQUEST_BODY_SIZE: String = "[Amplitude] Request Body Size"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY_SIZE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY_SIZE"),
        )
        public const val NETWORK_TRACKING_RESPONSE_BODY_SIZE: String = "[Amplitude] Response Body Size"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_HEADERS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_HEADERS"),
        )
        public const val NETWORK_TRACKING_REQUEST_HEADERS: String = "[Amplitude] Request Headers"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_HEADERS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_HEADERS"),
        )
        public const val NETWORK_TRACKING_RESPONSE_HEADERS: String = "[Amplitude] Response Headers"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY"),
        )
        public const val NETWORK_TRACKING_REQUEST_BODY: String = "[Amplitude] Request Body"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY"),
        )
        public const val NETWORK_TRACKING_RESPONSE_BODY: String = "[Amplitude] Response Body"

        // Accessibility properties
        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_ACCESSIBILITY_LABEL — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_ACCESSIBILITY_LABEL"),
        )
        public const val TARGET_ACCESSIBILITY_LABEL: String = "[Amplitude] Target Accessibility Label"

        // Frustration interactions properties
        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.BEGIN_TIME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.BEGIN_TIME"),
        )
        public const val BEGIN_TIME: String = "[Amplitude] Begin Time"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.END_TIME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.END_TIME"),
        )
        public const val END_TIME: String = "[Amplitude] End Time"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.DURATION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.DURATION"),
        )
        public const val DURATION: String = "[Amplitude] Duration"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.COORDINATE_X — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.COORDINATE_X"),
        )
        public const val COORDINATE_X: String = "[Amplitude] X"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.COORDINATE_Y — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.COORDINATE_Y"),
        )
        public const val COORDINATE_Y: String = "[Amplitude] Y"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.CLICKS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.CLICKS"),
        )
        public const val CLICKS: String = "[Amplitude] Clicks"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.CLICK_COUNT — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.CLICK_COUNT"),
        )
        public const val CLICK_COUNT: String = "[Amplitude] Click Count"
    }
}
