package com.amplitude.core.utilities.http

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

internal class HttpClient(
    private val configuration: Configuration,
    private val logger: Logger,
) : HttpClientInterface {
    private fun getConnection(url: String): HttpURLConnection {
        val requestedURL: URL =
            try {
                URL(url)
            } catch (e: MalformedURLException) {
                throw IOException("Attempted to use malformed url: $url", e)
            }
        val connection = requestedURL.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true
        connection.doInput = true
        connection.connectTimeout = 15_000 // 15s
        connection.readTimeout = 20_000 // 20s
        return connection
    }

    override fun upload(
        events: String,
        diagnostics: String?,
    ): AnalyticsResponse {
        val connection: HttpURLConnection = getConnection(configuration.getApiHost())
        val request = AnalyticsRequest(getApiKey(), events, configuration.minIdLength, diagnostics)
        val bodyString = request.getBodyStr()
        val input = bodyString.toByteArray()
        connection.outputStream.write(input, 0, input.size)
        connection.outputStream.close()

        val responseCode: Int = connection.responseCode
        var inputStream: InputStream? = null
        return try {
            inputStream = getInputStream(connection)
            val responseBody = inputStream.bufferedReader().use(BufferedReader::readText)
            AnalyticsResponse.create(responseCode, responseBody)
        } catch (e: IOException) {
            logger.error("Failed to read response from server: ${e.message}")
            AnalyticsResponse.create(408, null) // Request Timeout
        } finally {
            inputStream?.close()
            connection.disconnect()
        }
    }

    private fun getApiKey(): String {
        return configuration.apiKey
    }

    private fun getInputStream(connection: HttpURLConnection): InputStream {
        return try {
            connection.inputStream
        } catch (e: IOException) {
            logger.warn("Failed to get input stream, falling back to error stream: ${e.message}")
            connection.errorStream
        }
    }
}
