package com.amplitude.android

/**
 * Autocapture event-name and event-property constants for the Android SDK.
 *
 * These were previously hosted in `com.amplitude.core.Constants` but are only consumed by
 * `:android` (autocapture, network tracking, frustration interactions). Aliases in `:analytics-core`
 * remain for binary compatibility — see [com.amplitude.core.Constants.EventTypes] and
 * [com.amplitude.core.Constants.EventProperties].
 */
public object Constants {
    public object EventTypes {
        public const val APPLICATION_INSTALLED: String = "[Amplitude] Application Installed"
        public const val APPLICATION_UPDATED: String = "[Amplitude] Application Updated"
        public const val APPLICATION_OPENED: String = "[Amplitude] Application Opened"
        public const val APPLICATION_BACKGROUNDED: String = "[Amplitude] Application Backgrounded"
        public const val DEEP_LINK_OPENED: String = "[Amplitude] Deep Link Opened"
        public const val SCREEN_VIEWED: String = "[Amplitude] Screen Viewed"
        public const val FRAGMENT_VIEWED: String = "[Amplitude] Fragment Viewed"
        public const val ELEMENT_INTERACTED: String = "[Amplitude] Element Interacted"
        public const val NETWORK_TRACKING: String = "[Amplitude] Network Request"
        public const val RAGE_CLICK: String = "[Amplitude] Rage Click"
        public const val DEAD_CLICK: String = "[Amplitude] Dead Click"
    }

    public object EventProperties {
        public const val VERSION: String = "[Amplitude] Version"
        public const val BUILD: String = "[Amplitude] Build"
        public const val PREVIOUS_VERSION: String = "[Amplitude] Previous Version"
        public const val PREVIOUS_BUILD: String = "[Amplitude] Previous Build"
        public const val FROM_BACKGROUND: String = "[Amplitude] From Background"
        public const val LINK_URL: String = "[Amplitude] Link URL"
        public const val LINK_REFERRER: String = "[Amplitude] Link Referrer"
        public const val SCREEN_NAME: String = "[Amplitude] Screen Name"
        public const val FRAGMENT_CLASS: String = "[Amplitude] Fragment Class"
        public const val FRAGMENT_IDENTIFIER: String = "[Amplitude] Fragment Identifier"
        public const val FRAGMENT_TAG: String = "[Amplitude] Fragment Tag"
        public const val ACTION: String = "[Amplitude] Action"
        public const val TARGET_CLASS: String = "[Amplitude] Target Class"
        public const val TARGET_RESOURCE: String = "[Amplitude] Target Resource"
        public const val TARGET_TAG: String = "[Amplitude] Target Tag"
        public const val TARGET_TEXT: String = "[Amplitude] Target Text"
        public const val TARGET_SOURCE: String = "[Amplitude] Target Source"
        public const val HIERARCHY: String = "[Amplitude] Hierarchy"

        public const val NETWORK_TRACKING_URL: String = "[Amplitude] URL"
        public const val NETWORK_TRACKING_URL_QUERY: String = "[Amplitude] URL Query"
        public const val NETWORK_TRACKING_URL_FRAGMENT: String = "[Amplitude] URL Fragment"
        public const val NETWORK_TRACKING_REQUEST_METHOD: String = "[Amplitude] Request Method"
        public const val NETWORK_TRACKING_STATUS_CODE: String = "[Amplitude] Status Code"
        public const val NETWORK_TRACKING_ERROR_MESSAGE: String = "[Amplitude] Error Message"
        public const val NETWORK_TRACKING_START_TIME: String = "[Amplitude] Start Time"
        public const val NETWORK_TRACKING_COMPLETION_TIME: String = "[Amplitude] Completion Time"
        public const val NETWORK_TRACKING_DURATION: String = "[Amplitude] Duration"
        public const val NETWORK_TRACKING_REQUEST_BODY_SIZE: String = "[Amplitude] Request Body Size"
        public const val NETWORK_TRACKING_RESPONSE_BODY_SIZE: String = "[Amplitude] Response Body Size"
        public const val NETWORK_TRACKING_REQUEST_HEADERS: String = "[Amplitude] Request Headers"
        public const val NETWORK_TRACKING_RESPONSE_HEADERS: String = "[Amplitude] Response Headers"
        public const val NETWORK_TRACKING_REQUEST_BODY: String = "[Amplitude] Request Body"
        public const val NETWORK_TRACKING_RESPONSE_BODY: String = "[Amplitude] Response Body"

        // Accessibility properties
        public const val TARGET_ACCESSIBILITY_LABEL: String = "[Amplitude] Target Accessibility Label"

        // Frustration interactions properties
        public const val BEGIN_TIME: String = "[Amplitude] Begin Time"
        public const val END_TIME: String = "[Amplitude] End Time"
        public const val DURATION: String = "[Amplitude] Duration"
        public const val COORDINATE_X: String = "[Amplitude] X"
        public const val COORDINATE_Y: String = "[Amplitude] Y"
        public const val CLICKS: String = "[Amplitude] Clicks"
        public const val CLICK_COUNT: String = "[Amplitude] Click Count"
    }
}
