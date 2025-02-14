package com.amplitude.android.sample

import com.amplitude.android.Configuration
import com.amplitude.core.utilities.http.AnalyticsRequest
import com.amplitude.core.utilities.http.AnalyticsResponse
import com.amplitude.core.utilities.http.HttpClientInterface
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException

class CustomOkHttpClient : HttpClientInterface {
    private val okHttpClient = OkHttpClient()
    private lateinit var configuration: Configuration

    fun initialize(configuration: Configuration) {
        this.configuration = configuration
    }

    override fun upload(events: String, diagnostics: String?): AnalyticsResponse {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val ampRequest = AnalyticsRequest(
            configuration.apiKey,
            events,
            diagnostics = diagnostics,
            minIdLength = configuration.minIdLength
        )
        val formBody: RequestBody = RequestBody.create(mediaType, ampRequest.getBodyStr())
        val request: Request =
            Request.Builder().url(configuration.getApiHost()).post(formBody).build()

        try {
            val response = okHttpClient.newCall(request).execute()
            return AnalyticsResponse.create(response.code, response.body?.string())
            // Do something with the response.
        } catch (e: IOException) {
            e.printStackTrace()
            return AnalyticsResponse.create(500, null)
        }
    }
}
