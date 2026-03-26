package com.amplitude.android.sample

import com.amplitude.core.utilities.http.AnalyticsRequest
import com.amplitude.core.utilities.http.AnalyticsResponse
import com.amplitude.core.utilities.http.HttpClientInterface
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class CustomOkHttpClient(
    private val apiKey: String,
    private val apiHost: String,
    private val minIdLength: Int? = null,
    private val okHttpClient: OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(GzipRequestInterceptor())
            .build(),
) : HttpClientInterface {
    override fun upload(
        events: String,
        diagnostics: String?,
    ): AnalyticsResponse {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val ampRequest =
            AnalyticsRequest(
                apiKey,
                events,
                diagnostics = diagnostics,
                minIdLength = minIdLength,
            )
        val formBody: RequestBody =
            ampRequest.getBodyStr()
                .toRequestBody(mediaType)
        val request: Request =
            Request.Builder()
                .url(apiHost)
                .post(formBody)
                .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                AnalyticsResponse.create(response.code, response.body?.string())
            }
        } catch (e: IOException) {
            e.printStackTrace()
            AnalyticsResponse.create(500, null)
        }
    }
}
