package com.amplitude.core

object Constants {
    const val SDK_LIBRARY = "amplitude-kotlin"
    const val SDK_VERSION = "0.0.1"
    const val DEFAULT_API_HOST = "https://api2.amplitude.com/2/httpapi"
    const val EU_DEFAULT_API_HOST = "https://api.eu.amplitude.com/2/httpapi"
    const val BATCH_API_HOST = "https://api2.amplitude.com/batch"
    const val EU_BATCH_API_HOST = "https://api.eu.amplitude.com/batch"
    const val IDENTIFY_EVENT = "\$identify"
    const val GROUP_IDENTIFY_EVENT = "\$groupidentify"
    const val AMP_REVENUE_EVENT = "revenue_amount"
    const val MAX_PROPERTY_KEYS = 1024
    const val MAX_STRING_LENGTH = 1024

    @Deprecated(
        "Moved to com.amplitude.android.Constants.EventTypes — requires the analytics-android dependency",
        ReplaceWith("com.amplitude.android.Constants.EventTypes"),
    )
    object EventTypes {
        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.APPLICATION_INSTALLED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.APPLICATION_INSTALLED"),
        )
        const val APPLICATION_INSTALLED = "[Amplitude] Application Installed"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.APPLICATION_UPDATED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.APPLICATION_UPDATED"),
        )
        const val APPLICATION_UPDATED = "[Amplitude] Application Updated"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.APPLICATION_OPENED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.APPLICATION_OPENED"),
        )
        const val APPLICATION_OPENED = "[Amplitude] Application Opened"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.APPLICATION_BACKGROUNDED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.APPLICATION_BACKGROUNDED"),
        )
        const val APPLICATION_BACKGROUNDED = "[Amplitude] Application Backgrounded"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.DEEP_LINK_OPENED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.DEEP_LINK_OPENED"),
        )
        const val DEEP_LINK_OPENED = "[Amplitude] Deep Link Opened"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.SCREEN_VIEWED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.SCREEN_VIEWED"),
        )
        const val SCREEN_VIEWED = "[Amplitude] Screen Viewed"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.FRAGMENT_VIEWED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.FRAGMENT_VIEWED"),
        )
        const val FRAGMENT_VIEWED = "[Amplitude] Fragment Viewed"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED"),
        )
        const val ELEMENT_INTERACTED = "[Amplitude] Element Interacted"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.NETWORK_TRACKING — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.NETWORK_TRACKING"),
        )
        const val NETWORK_TRACKING = "[Amplitude] Network Request"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.RAGE_CLICK — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.RAGE_CLICK"),
        )
        const val RAGE_CLICK = "[Amplitude] Rage Click"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventTypes.DEAD_CLICK — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventTypes.DEAD_CLICK"),
        )
        const val DEAD_CLICK = "[Amplitude] Dead Click"
    }

    @Deprecated(
        "Moved to com.amplitude.android.Constants.EventProperties — requires the analytics-android dependency",
        ReplaceWith("com.amplitude.android.Constants.EventProperties"),
    )
    object EventProperties {
        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.VERSION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.VERSION"),
        )
        const val VERSION = "[Amplitude] Version"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.BUILD — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.BUILD"),
        )
        const val BUILD = "[Amplitude] Build"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.PREVIOUS_VERSION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.PREVIOUS_VERSION"),
        )
        const val PREVIOUS_VERSION = "[Amplitude] Previous Version"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.PREVIOUS_BUILD — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.PREVIOUS_BUILD"),
        )
        const val PREVIOUS_BUILD = "[Amplitude] Previous Build"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.FROM_BACKGROUND — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.FROM_BACKGROUND"),
        )
        const val FROM_BACKGROUND = "[Amplitude] From Background"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.LINK_URL — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.LINK_URL"),
        )
        const val LINK_URL = "[Amplitude] Link URL"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.LINK_REFERRER — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.LINK_REFERRER"),
        )
        const val LINK_REFERRER = "[Amplitude] Link Referrer"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.SCREEN_NAME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.SCREEN_NAME"),
        )
        const val SCREEN_NAME = "[Amplitude] Screen Name"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.FRAGMENT_CLASS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.FRAGMENT_CLASS"),
        )
        const val FRAGMENT_CLASS = "[Amplitude] Fragment Class"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.FRAGMENT_IDENTIFIER — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.FRAGMENT_IDENTIFIER"),
        )
        const val FRAGMENT_IDENTIFIER = "[Amplitude] Fragment Identifier"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.FRAGMENT_TAG — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.FRAGMENT_TAG"),
        )
        const val FRAGMENT_TAG = "[Amplitude] Fragment Tag"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.ACTION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.ACTION"),
        )
        const val ACTION = "[Amplitude] Action"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_CLASS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_CLASS"),
        )
        const val TARGET_CLASS = "[Amplitude] Target Class"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_RESOURCE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_RESOURCE"),
        )
        const val TARGET_RESOURCE = "[Amplitude] Target Resource"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_TAG — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_TAG"),
        )
        const val TARGET_TAG = "[Amplitude] Target Tag"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_TEXT — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_TEXT"),
        )
        const val TARGET_TEXT = "[Amplitude] Target Text"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_SOURCE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_SOURCE"),
        )
        const val TARGET_SOURCE = "[Amplitude] Target Source"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.HIERARCHY — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.HIERARCHY"),
        )
        const val HIERARCHY = "[Amplitude] Hierarchy"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL"),
        )
        const val NETWORK_TRACKING_URL = "[Amplitude] URL"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_QUERY — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_QUERY"),
        )
        const val NETWORK_TRACKING_URL_QUERY = "[Amplitude] URL Query"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_FRAGMENT — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_URL_FRAGMENT"),
        )
        const val NETWORK_TRACKING_URL_FRAGMENT = "[Amplitude] URL Fragment"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_METHOD — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_METHOD"),
        )
        const val NETWORK_TRACKING_REQUEST_METHOD = "[Amplitude] Request Method"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_STATUS_CODE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_STATUS_CODE"),
        )
        const val NETWORK_TRACKING_STATUS_CODE = "[Amplitude] Status Code"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_ERROR_MESSAGE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_ERROR_MESSAGE"),
        )
        const val NETWORK_TRACKING_ERROR_MESSAGE = "[Amplitude] Error Message"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_START_TIME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_START_TIME"),
        )
        const val NETWORK_TRACKING_START_TIME = "[Amplitude] Start Time"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_COMPLETION_TIME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_COMPLETION_TIME"),
        )
        const val NETWORK_TRACKING_COMPLETION_TIME = "[Amplitude] Completion Time"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_DURATION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_DURATION"),
        )
        const val NETWORK_TRACKING_DURATION = "[Amplitude] Duration"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY_SIZE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY_SIZE"),
        )
        const val NETWORK_TRACKING_REQUEST_BODY_SIZE = "[Amplitude] Request Body Size"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY_SIZE — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY_SIZE"),
        )
        const val NETWORK_TRACKING_RESPONSE_BODY_SIZE = "[Amplitude] Response Body Size"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_HEADERS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_HEADERS"),
        )
        const val NETWORK_TRACKING_REQUEST_HEADERS = "[Amplitude] Request Headers"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_HEADERS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_HEADERS"),
        )
        const val NETWORK_TRACKING_RESPONSE_HEADERS = "[Amplitude] Response Headers"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_REQUEST_BODY"),
        )
        const val NETWORK_TRACKING_REQUEST_BODY = "[Amplitude] Request Body"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.NETWORK_TRACKING_RESPONSE_BODY"),
        )
        const val NETWORK_TRACKING_RESPONSE_BODY = "[Amplitude] Response Body"

        // Accessibility properties
        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.TARGET_ACCESSIBILITY_LABEL — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.TARGET_ACCESSIBILITY_LABEL"),
        )
        const val TARGET_ACCESSIBILITY_LABEL = "[Amplitude] Target Accessibility Label"

        // Frustration interactions properties
        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.BEGIN_TIME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.BEGIN_TIME"),
        )
        const val BEGIN_TIME = "[Amplitude] Begin Time"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.END_TIME — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.END_TIME"),
        )
        const val END_TIME = "[Amplitude] End Time"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.DURATION — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.DURATION"),
        )
        const val DURATION = "[Amplitude] Duration"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.COORDINATE_X — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.COORDINATE_X"),
        )
        const val COORDINATE_X = "[Amplitude] X"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.COORDINATE_Y — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.COORDINATE_Y"),
        )
        const val COORDINATE_Y = "[Amplitude] Y"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.CLICKS — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.CLICKS"),
        )
        const val CLICKS = "[Amplitude] Clicks"

        @Deprecated(
            "Moved to com.amplitude.android.Constants.EventProperties.CLICK_COUNT — " +
                "requires the analytics-android dependency",
            ReplaceWith("com.amplitude.android.Constants.EventProperties.CLICK_COUNT"),
        )
        const val CLICK_COUNT = "[Amplitude] Click Count"
    }
}
