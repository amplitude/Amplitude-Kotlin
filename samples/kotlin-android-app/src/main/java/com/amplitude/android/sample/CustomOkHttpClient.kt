package com.amplitude.android.sample

import com.amplitude.core.utilities.http.AnalyticsRequest
import com.amplitude.core.utilities.http.AnalyticsResponse
import com.amplitude.core.utilities.http.HttpClientInterface
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException


class CustomOkHttpClient(val apiKey: String) : HttpClientInterface {
    private val okHttpClient = OkHttpClient()

    override fun upload(events: String, diagnostics: String?): AnalyticsResponse {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val ampRequest = AnalyticsRequest(apiKey, events, diagnostics = diagnostics)
        val formBody: RequestBody = RequestBody.create(mediaType, ampRequest.getBodyStr())
        val request: Request =
            Request.Builder().url("https://api.amplitude.com/2/httpapi").post(formBody).build()

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
