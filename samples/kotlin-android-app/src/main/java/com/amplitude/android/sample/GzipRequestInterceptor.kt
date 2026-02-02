package com.amplitude.android.sample

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

/**
 * OkHttp Interceptor that compresses request bodies using gzip.
 *
 * Usage:
 * ```
 * val okHttpClient = OkHttpClient.Builder()
 *     .addInterceptor(GzipRequestInterceptor())
 *     .build()
 * ```
 */
class GzipRequestInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val body = originalRequest.body

        // Skip if no body or already compressed
        if (body == null || originalRequest.header("Content-Encoding") != null) {
            return chain.proceed(originalRequest)
        }

        val compressedRequest =
            originalRequest.newBuilder()
                .header("Content-Encoding", "gzip")
                .method(originalRequest.method, gzip(body))
                .build()

        return chain.proceed(compressedRequest)
    }

    private fun gzip(body: RequestBody): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? = body.contentType()

            override fun contentLength(): Long = -1 // Unknown after compression

            override fun writeTo(sink: BufferedSink) {
                GzipSink(sink).buffer().use { gzipSink ->
                    body.writeTo(gzipSink)
                }
            }
        }
    }
}
