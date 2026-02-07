package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File

class DiagnosticsRemoteConfigTest {
    @Test
    fun `remote config enables diagnostics`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            every { httpClient.request(any()) } returns HttpClient.Response(200, "ok")
            val remoteConfigClient = TestRemoteConfigClient()

            val client =
                createClient(
                    httpClient = httpClient,
                    remoteConfigClient = remoteConfigClient,
                    enabled = false,
                    sampleRate = 1.0,
                )
            delay(100)

            remoteConfigClient.emit(mapOf("enabled" to true, "sampleRate" to 1.0))
            delay(100)

            client.setTag("tag", "value")
            client.increment("counter", 1) // Need metric data to trigger upload
            client.flush()
            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }
        }

    @Test
    fun `remote config disables diagnostics`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            every { httpClient.request(any()) } returns HttpClient.Response(200, "ok")
            val remoteConfigClient = TestRemoteConfigClient()

            val client =
                createClient(
                    httpClient = httpClient,
                    remoteConfigClient = remoteConfigClient,
                    enabled = true,
                    sampleRate = 1.0,
                )
            delay(100)

            remoteConfigClient.emit(mapOf("enabled" to false))
            delay(100)

            client.setTag("tag", "value")
            client.flush()
            delay(200)

            verify(exactly = 0) { httpClient.request(any()) }
        }

    @Test
    fun `remote config updates sample rate`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            every { httpClient.request(any()) } returns HttpClient.Response(200, "ok")
            val remoteConfigClient = TestRemoteConfigClient()

            val client =
                createClient(
                    httpClient = httpClient,
                    remoteConfigClient = remoteConfigClient,
                    enabled = true,
                    sampleRate = 0.0,
                )
            delay(100)

            remoteConfigClient.emit(mapOf("sampleRate" to 1.0))
            delay(100)

            client.recordEvent("event", null)
            client.flush()
            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }
        }

    @Test
    fun `remote config ignores invalid types`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            every { httpClient.request(any()) } returns HttpClient.Response(200, "ok")
            val remoteConfigClient = TestRemoteConfigClient()

            val client =
                createClient(
                    httpClient = httpClient,
                    remoteConfigClient = remoteConfigClient,
                    enabled = true,
                    sampleRate = 0.0,
                )
            delay(100)

            remoteConfigClient.emit(mapOf("enabled" to "true", "sampleRate" to "1.0"))
            delay(100)

            client.recordEvent("event", null)
            client.flush()
            delay(200)

            verify(exactly = 0) { httpClient.request(any()) }
        }

    private fun createClient(
        httpClient: HttpClient,
        remoteConfigClient: RemoteConfigClient,
        enabled: Boolean,
        sampleRate: Double,
    ): DiagnosticsClientImpl {
        val logger = mockk<Logger>(relaxed = true)
        val storageDir = File.createTempFile("diag-remote-config", "test").parentFile
        return DiagnosticsClientImpl(
            apiKey = "test-api-key",
            serverZone = ServerZone.US,
            instanceName = "test-instance",
            storageDirectory = storageDir,
            logger = logger,
            coroutineScope = CoroutineScope(Dispatchers.Default),
            networkIODispatcher = Dispatchers.IO,
            storageIODispatcher = Dispatchers.IO,
            remoteConfigClient = remoteConfigClient,
            httpClient = httpClient,
            contextProvider = null,
            enabled = enabled,
            sampleRate = sampleRate,
            flushIntervalMillis = 60_000,
            storageIntervalMillis = 60_000,
        )
    }

    private class TestRemoteConfigClient : RemoteConfigClient {
        private val callbacks = mutableListOf<RemoteConfigClient.RemoteConfigCallback>()
        var updateCalls: Int = 0

        override fun subscribe(
            key: RemoteConfigClient.Key,
            callback: RemoteConfigClient.RemoteConfigCallback,
        ) {
            if (key == RemoteConfigClient.Key.DIAGNOSTICS) {
                callbacks.add(callback)
            }
        }

        override fun updateConfigs() {
            updateCalls += 1
        }

        fun emit(
            config: ConfigMap,
            source: RemoteConfigClient.Source = RemoteConfigClient.Source.REMOTE,
            timestamp: Long = System.currentTimeMillis(),
        ) {
            callbacks.forEach { it.onUpdate(config, source, timestamp) }
        }
    }
}
