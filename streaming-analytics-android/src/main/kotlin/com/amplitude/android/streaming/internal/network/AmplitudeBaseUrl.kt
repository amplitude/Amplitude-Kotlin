package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.core.Configuration
import com.amplitude.core.Constants
import com.amplitude.core.ServerZone
import java.net.URI
import java.net.URL

internal val StreamingDiGraph.amplitudeBaseUrl: AmplitudeBaseUrl by weak {
    AmplitudeBaseUrl(configuration)
}

internal class AmplitudeBaseUrl(
    private val configuration: Configuration,
) {
    fun url(vararg extraPathSegments: String): URL {
        val base =
            configuration.serverUrl?.takeIf { it.isNotBlank() }
                ?: when (configuration.serverZone) {
                    ServerZone.US -> Constants.DEFAULT_API_HOST
                    ServerZone.EU -> Constants.EU_DEFAULT_API_HOST
                }
        val uri = URI(base)
        val basePath = uri.rawPath.orEmpty().trimEnd('/').ifEmpty { "/2/httpapi" }
        val extra =
            extraPathSegments
                .map { it.trim('/') }
                .filter { it.isNotEmpty() }
                .joinToString("/")
        val path = if (extra.isEmpty()) basePath else "$basePath/$extra"
        return URL(URI(uri.scheme, uri.authority, path, uri.query, uri.fragment).toString())
    }
}
