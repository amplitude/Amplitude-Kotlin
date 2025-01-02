package com.amplitude.core.utilities

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Request(
    val apiKey: String,
    val clientUploadTime: Long,
    val events: String,
    val minIdLength: Int?,
    val diagnostics: String?
) {
    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun getBodyStr(): String {
        return buildString {
            append("{\"api_key\":\"$apiKey\",\"client_upload_time\":\"$clientUploadTime\",\"events\":$events")
            if (minIdLength != null) {
                append(",\"options\":{\"min_id_length\":$minIdLength}")
            }
            if (diagnostics != null) {
                append(",\"request_metadata\":{\"sdk\":$diagnostics}")
            }
            append("}")
        }
    }

    internal fun getClientUploadTime(): String {
        return sdf.format(Date(clientUploadTime))
    }
}