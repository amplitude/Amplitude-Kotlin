package com.amplitude

object Constants {
    const val SDK_LIBRARY = "amplitude-kotlin"
    const val SDK_VERSION = "0.0.1"
    const val DEFAULT_API_HOST = "https://api2.amplitude.com/2/httpapi"
    const val EU_DEFAULT_API_HOST = "https://api.eu.amplitude.com/2/httpapi"
    const val FLUSH_QUEUE_SIZE = 30
    const val FLUSH_INTERVAL_MILLIS = 30 * 1000 // 30s
    const val IDENTIFY_EVENT = "\$identify"
    const val GROUP_IDENTIFY_EVENT = "\$groupidentify"
    const val AMP_REVENUE_EVENT = "revenue_amount"
    const val MAX_PROPERTY_KEYS = 1024
    const val MAX_STRING_LENGTH = 1024
}