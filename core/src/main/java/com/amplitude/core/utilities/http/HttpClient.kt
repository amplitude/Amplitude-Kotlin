package com.amplitude.core.utilities.http

import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.utilities.http.HttpClient.Request.Method.POST
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
    override fun upload(
        events: String,
        diagnostics: String?,
    ): AnalyticsResponse {
        val url = configuration.getApiHost()
        val requestBody =
            AnalyticsRequest(
                getApiKey(),
                events,
                configuration.minIdLength,
                diagnostics,
            ).getBodyStr()
        val request = Request(url, POST, body = requestBody)
        val httpResponse = request(request)
        return AnalyticsResponse.create(httpResponse.statusCode, httpResponse.body)
    }

    /**
     * Internal method for making generic HTTP requests.
     * [com.amplitude.core.utilities.http.HttpClient.Request.DEFAULT_HEADERS] are automatically
     * added to all requests and can be overridden by custom headers.
     */
    internal fun request(request: Request): Response {
        val requestedURL: URL =
            try {
                URL(request.url)
            } catch (e: MalformedURLException) {
                logger.error("Attempted to use malformed url: ${request.url}, error: ${e.message}")
                return Response(400, null, emptyMap(), "Malformed URL")
            }
        val connection: HttpURLConnection =
            try {
                requestedURL.openConnection() as HttpURLConnection
            } catch (e: IOException) {
                logger.error("Failed to open connection: ${e.message}")
                return Response(500, null, emptyMap(), "Connection failed")
            }
        try {
            // Set request method, connect and read timeouts
            connection.requestMethod = request.method.name
            connection.connectTimeout = request.connectTimeoutMs
            connection.readTimeout = request.readTimeoutMs
            connection.doInput = true

            // Set default and custom headers (overrides default if same key)
            Request.DEFAULT_HEADERS.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            request.headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            // Set request body if present
            request.body?.let { body ->
                connection.doOutput = true
                val input = body.toByteArray()
                connection.outputStream.write(input, 0, input.size)
                connection.outputStream.close()
            }

            // Read response
            val responseCode: Int = connection.responseCode
            val responseMessage: String? = connection.responseMessage
            val responseBody: String?
            var inputStream: InputStream? = null
            try {
                inputStream = getInputStream(connection)
                responseBody = inputStream.bufferedReader().use(BufferedReader::readText)
            } catch (e: IOException) {
                logger.error("Failed to read response from server: ${e.message}")
                return Response(408, null, emptyMap(), "Request timeout")
            } finally {
                inputStream?.close()
            }

            // Extract headers
            val headers = mutableMapOf<String, List<String>>()
            for ((key, values) in connection.headerFields) {
                if (key != null) {
                    headers[key] = values ?: emptyList()
                }
            }

            return Response(responseCode, responseBody, headers, responseMessage)
        } catch (e: Exception) {
            logger.error("Request failed: ${e.message}")
            return Response(500, null, emptyMap(), "Request failed: ${e.message}")
        } finally {
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

    /**
     * Generic HTTP request data class for making various types of HTTP requests.
     */
    internal data class Request(
        val url: String,
        val method: Method,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    ) {
        enum class Method {
            GET,
            POST,
            PUT,
            DELETE,
            PATCH,
        }

        companion object {
            /**
             * Default headers automatically added to all HTTP requests.
             */
            val DEFAULT_HEADERS: Map<String, String> =
                mapOf(
                    "Content-Type" to "application/json; charset=utf-8",
                    "Accept" to "application/json",
                )
            const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
            const val DEFAULT_READ_TIMEOUT_MS = 20_000
        }
    }

    /**
     * Generic HTTP response data class for handling various types of HTTP responses.
     */
    internal data class Response(
        val statusCode: Int,
        val body: String?,
        val headers: Map<String, List<String>> = emptyMap(),
        val statusMessage: String? = null,
    ) {
        val isSuccessful: Boolean
            get() = statusCode in 200..299
        val isClientError: Boolean
            get() = statusCode in 400..499
        val isServerError: Boolean
            get() = statusCode in 500..599
    }
}
