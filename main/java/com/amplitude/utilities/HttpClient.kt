package com.amplitude.utilities

import com.amplitude.Configuration
import com.amplitude.Constants
import com.amplitude.events.BaseEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

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
                    if (responseCode == HttpStatus.SUCCESS.code) {
                        var responseBody: String?
                        var inputStream: InputStream? = null
                        try {
                            inputStream = getInputStream(this.connection)
                            responseBody = inputStream?.bufferedReader()?.use(BufferedReader::readText)
                        } catch (e: IOException) {
                            responseBody = ("Could not read response body for rejected message: "
                                    + e.toString())
                        } finally {
                            inputStream?.close()
                        }
                        // @TODO: handle failures
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
            val input = bodyString.toByteArray(StandardCharsets.UTF_8)
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

internal enum class HttpStatus(val code: Int) {
    SUCCESS(200),
    BAD_REQUEST(400),
    TIMEOUT(408),
    PAYLOAD_TOO_LARGE(413),
    TOO_MANY_REQUESTS(429),
    FAILED(500)
}