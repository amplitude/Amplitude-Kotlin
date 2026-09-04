package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.StreamingDiGraph
import com.amplitude.android.streaming.internal.util.DiGraph.Companion.weak
import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URISyntaxException

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val CONNECT_TIMEOUT_MILLIS = 15_000
private const val READ_TIMEOUT_MILLIS = 20_000

internal val StreamingDiGraph.delayedEventsEndpoint: DelayedEventsEndpoint by weak {
    DelayedEventsEndpoint(
        configuration = configuration,
        delayedEventsBaseUrl = delayedEventsBaseUrl,
        logger = logger,
        ioDispatcher = ioDispatcher,
    )
}

internal class DelayedEventsEndpoint(
    private val configuration: Configuration,
    private val delayedEventsBaseUrl: DelayedEventsBaseUrl,
    private val logger: Logger,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun send(request: DelayedEventsRequestDto): DelayedEventsResult =
        withContext(ioDispatcher) {
            val requestBody =
                try {
                    request.toJson(configuration.apiKey)
                } catch (error: SerializationException) {
                    logger.error("Failed to serialize delayed-events request: ${error.message}")
                    return@withContext DelayedEventsResult.Failure(statusCode = null, message = error.message)
                }

            val connection =
                try {
                    delayedEventsBaseUrl.url("delayed").openConnection() as HttpURLConnection
                } catch (error: URISyntaxException) {
                    logger.error("Invalid delayed-events URI: ${error.message}")
                    return@withContext DelayedEventsResult.Failure(statusCode = null, message = error.message)
                } catch (error: IOException) {
                    logger.error("Failed to open delayed-events connection: ${error.message}")
                    return@withContext DelayedEventsResult.Failure(statusCode = null, message = error.message)
                }

            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.doInput = true
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.outputStream.use {
                    it.write(requestBody.toByteArray(Charsets.UTF_8))
                }
                val statusCode = connection.responseCode
                when (statusCode) {
                    in 200..299 -> DelayedEventsResult.Success
                    HTTP_TOO_MANY_REQUESTS -> DelayedEventsResult.RateLimited
                    else ->
                        DelayedEventsResult.Failure(
                            statusCode = statusCode,
                            message =
                                readBody(connection)
                                    ?.takeIf { it.isNotBlank() }
                                    ?: connection.responseMessage,
                        )
                }
            } catch (error: IOException) {
                logger.error("Delayed-events request failed: ${error.message}")
                DelayedEventsResult.Failure(statusCode = null, message = error.message)
            } finally {
                connection.disconnect()
            }
        }

    private fun readBody(connection: HttpURLConnection): String? {
        val stream: InputStream? =
            try {
                connection.inputStream
            } catch (_: IOException) {
                connection.errorStream
            }
        return try {
            stream?.bufferedReader()
                ?.use { it.readText() }
        } catch (_: IOException) {
            null
        }
    }
}
