package com.amplitude.core.utilities.http

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

public data class AnalyticsRequest(
    val apiKey: String,
    val events: String,
    val minIdLength: Int? = null,
    val diagnostics: String? = null,
    val clientUploadTime: Long = System.currentTimeMillis(),
) {
    private val sdf =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    public fun getBodyStr(): String {
        return bodyParts().joinToString(separator = "")
    }

    /**
     * The request body split at the [events] boundary, to be written in order.
     *
     * [events] can approach [com.amplitude.core.utilities.EventsFileManager.MAX_FILE_SIZE].
     * Joining it into one body string costs another copy of it in the growing [StringBuilder]
     * plus one more in the resulting [String], so callers that can stream should use this and
     * never materialize the joined body.
     */
    internal fun bodyParts(): List<String> =
        buildList {
            add("{\"api_key\":\"$apiKey\",\"client_upload_time\":\"${getClientUploadTime()}\",\"events\":")
            add(events)
            if (minIdLength != null) {
                add(",\"options\":{\"min_id_length\":$minIdLength}")
            }
            if (diagnostics != null) {
                add(",\"request_metadata\":{\"sdk\":$diagnostics}")
            }
            add("}")
        }

    internal fun getClientUploadTime(): String {
        return sdf.format(Date(clientUploadTime))
    }
}
