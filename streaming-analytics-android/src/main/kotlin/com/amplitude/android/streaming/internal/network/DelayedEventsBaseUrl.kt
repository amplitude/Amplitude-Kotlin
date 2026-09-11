package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.core.Configuration
import com.amplitude.core.ServerZone
import java.net.URI
import java.net.URL

private const val US_DEFAULT_HOST = "https://delayed-events.prod.us-west-2.amplitude.com/2/httpapi"
private const val EU_DEFAULT_HOST = "https://delayed-events.prod.eu-central-1.amplitude.com/2/httpapi"

internal val StreamingDiGraph.delayedEventsBaseUrl: DelayedEventsBaseUrl by weak {
    DelayedEventsBaseUrl(configuration)
}

internal class DelayedEventsBaseUrl(
    private val configuration: Configuration,
) {
    fun url(vararg extraPathSegments: String): URL {
        val base =
            configuration.serverUrl?.takeIf { it.isNotBlank() }
                ?: when (configuration.serverZone) {
                    ServerZone.US -> US_DEFAULT_HOST
                    ServerZone.EU -> EU_DEFAULT_HOST
                }
        val uri = URI(base)
        val basePath = uri.rawPath.orEmpty().trimEnd('/')
        val extra =
            extraPathSegments
                .map { it.trim('/') }
                .filter { it.isNotEmpty() }
                .joinToString("/")
        val path = if (extra.isEmpty()) basePath else "$basePath/$extra"
        return URL(
            buildString {
                append(uri.scheme)
                append("://")
                append(uri.rawAuthority)
                append(path)
                if (!uri.rawQuery.isNullOrEmpty()) {
                    append('?')
                    append(uri.rawQuery)
                }
                if (!uri.rawFragment.isNullOrEmpty()) {
                    append('#')
                    append(uri.rawFragment)
                }
            },
        )
    }
}
