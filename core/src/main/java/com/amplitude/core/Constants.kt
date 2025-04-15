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

    object EventTypes {
        const val APPLICATION_INSTALLED = "[Amplitude] Application Installed"
        const val APPLICATION_UPDATED = "[Amplitude] Application Updated"
        const val APPLICATION_OPENED = "[Amplitude] Application Opened"
        const val APPLICATION_BACKGROUNDED = "[Amplitude] Application Backgrounded"
        const val DEEP_LINK_OPENED = "[Amplitude] Deep Link Opened"
        const val SCREEN_VIEWED = "[Amplitude] Screen Viewed"
        const val FRAGMENT_VIEWED = "[Amplitude] Fragment Viewed"
        const val ELEMENT_INTERACTED = "[Amplitude] Element Interacted"
    }

    object EventProperties {
        const val VERSION = "[Amplitude] Version"
        const val BUILD = "[Amplitude] Build"
        const val PREVIOUS_VERSION = "[Amplitude] Previous Version"
        const val PREVIOUS_BUILD = "[Amplitude] Previous Build"
        const val FROM_BACKGROUND = "[Amplitude] From Background"
        const val LINK_URL = "[Amplitude] Link URL"
        const val LINK_REFERRER = "[Amplitude] Link Referrer"
        const val SCREEN_NAME = "[Amplitude] Screen Name"
        const val FRAGMENT_CLASS = "[Amplitude] Fragment Class"
        const val FRAGMENT_IDENTIFIER = "[Amplitude] Fragment Identifier"
        const val FRAGMENT_TAG = "[Amplitude] Fragment Tag"
        const val ACTION = "[Amplitude] Action"
        const val TARGET_CLASS = "[Amplitude] Target Class"
        const val TARGET_RESOURCE = "[Amplitude] Target Resource"
        const val TARGET_TAG = "[Amplitude] Target Tag"
        const val TARGET_TEXT = "[Amplitude] Target Text"
        const val TARGET_SOURCE = "[Amplitude] Target Source"
        const val HIERARCHY = "[Amplitude] Hierarchy"
    }
}
