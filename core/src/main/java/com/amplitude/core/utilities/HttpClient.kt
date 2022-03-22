package com.amplitude.core.utilities

import com.amplitude.core.Configuration
import com.amplitude.core.Constants
import org.json.JSONObject
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

internal class HttpClient(
    private val configuration: Configuration
) {

    fun upload(): Connection {
        val connection: HttpURLConnection = getConnection(getApiHost())
        val outputStream: OutputStream = connection.outputStream

        return object : Connection(connection, null, outputStream) {
            @Throws(IOException::class)
            override fun close() {
                try {
                    this.setApiKey(getApiKey())
                    this.setMinIdLength(getMindIdLength())
                    this.setBody()
                    this.outputStream?.close()
                    val responseCode: Int = connection.responseCode
                    var responseBody: String?
                    var inputStream: InputStream? = null
                    try {
                        inputStream = getInputStream(this.connection)
                        responseBody = inputStream?.bufferedReader()?.use(BufferedReader::readText)
                        this.response = HttpResponse.createHttpResponse(responseCode, JSONObject(responseBody))
                    } catch (e: IOException) {
                        this.response = HttpResponse.createHttpResponse(408, null)
                    } finally {
                        inputStream?.close()
                    }
                } finally {
                    super.close()
                    this.outputStream?.close()
                }
            }
        }
    }

    private fun getConnection(url: String): HttpURLConnection {
        val requestedURL: URL = try {
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
        connection.readTimeout = 20_1000 // 20s
        return connection
    }

    private fun getApiHost(): String {
        return Constants.DEFAULT_API_HOST
    }

    internal fun getApiKey(): String {
        return configuration.apiKey
    }

    internal fun getMindIdLength(): Int? {
        return configuration.minIdLength
    }

    fun getInputStream(connection: HttpURLConnection): InputStream {
        return try {
            connection.inputStream
        } catch (ignored: IOException) {
            connection.errorStream
        }
    }
}

abstract class Connection(
    val connection: HttpURLConnection,
    val inputStream: InputStream?,
    val outputStream: OutputStream?
) : Closeable {

    private lateinit var apiKey: String
    private lateinit var events: String
    private var minIdLength: Int? = null
    internal lateinit var response: Response

    @Throws(IOException::class)
    override fun close() {
        connection.disconnect()
    }

    internal fun setApiKey(apiKey: String) {
        this.apiKey = apiKey
    }

    internal fun setMinIdLength(minIdLength: Int?) {
        this.minIdLength = minIdLength
    }

    internal fun setEvents(events: String) {
        this.events = events
    }

    internal fun setBody() {
        this.outputStream?.let {
            val bodyString = getBodyStr()
            val input = bodyString.toByteArray()
            this.outputStream.write(input, 0, input.size)
        }
    }

    private fun getBodyStr(): String {
        if (minIdLength == null) {
            return "{\"api_key\":\"$apiKey\",\"events\":$events}"
        }
        return "{\"api_key\":\"$apiKey\",\"events\":$events,\"options\":{\"min_id_length\":$minIdLength}}"
    }
}

enum class HttpStatus(val code: Int) {
    SUCCESS(200),
    BAD_REQUEST(400),
    TIMEOUT(408),
    PAYLOAD_TOO_LARGE(413),
    TOO_MANY_REQUESTS(429),
    FAILED(500)
}
