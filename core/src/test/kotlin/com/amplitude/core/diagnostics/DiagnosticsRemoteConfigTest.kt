package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DiagnosticsRemoteConfigTest {
    @Test
    fun `remote config enables diagnostics`() =
        runTest {
            val httpClient = mockk<HttpClient>()
            every { httpClient.request(any()) } returns HttpClient.Response(200, "ok")
            val remoteConfigClient = TestRemoteConfigClient()

            val client =
                createClient(
                    httpClient = httpClient,
                    remoteConfigClient = remoteConfigClient,
                    enabled = false,
                    sampleRate = 1.0,
                    testScope = this,
                )
            testScheduler.runCurrent()

            remoteConfigClient.emit(mapOf("enabled" to true, "sampleRate" to 1.0))
            testScheduler.runCurrent()

            client.setTag("tag", "value")
            client.flush()

            verify(exactly = 1) { httpClient.request(any()) }
        }

    @Test
    fun `remote config disables diagnostics`() =
        runTest {
            val httpClient = mockk<HttpClient>()
            every { httpClient.request(any()) } returns HttpClient.Response(200, "ok")
            val remoteConfigClient = TestRemoteConfigClient()

            val client =
                createClient(
                    httpClient = httpClient,
                    remoteConfigClient = remoteConfigClient,
                    enabled = true,
                    sampleRate = 1.0,
                    testScope = this,
                )
            testScheduler.runCurrent()

            remoteConfigClient.emit(mapOf("enabled" to false))
            testScheduler.runCurrent()

            client.setTag("tag", "value")
            client.flush()

            verify(exactly = 0) { httpClient.request(any()) }
        }

    @Test
    fun `remote config updates sample rate`() =
        runTest {
            val httpClient = mockk<HttpClient>()
            every { httpClient.request(any()) } returns HttpClient.Response(200, "ok")
            val remoteConfigClient = TestRemoteConfigClient()

            val client =
                createClient(
                    httpClient = httpClient,
                    remoteConfigClient = remoteConfigClient,
                    enabled = true,
                    sampleRate = 0.0,
                    testScope = this,
                )
            testScheduler.runCurrent()

            remoteConfigClient.emit(mapOf("sampleRate" to 1.0))
            testScheduler.runCurrent()

            client.recordEvent("event", null)
            client.flush()

            verify(exactly = 1) { httpClient.request(any()) }
        }

    @Test
    fun `remote config ignores invalid types`() =
        runTest {
            val httpClient = mockk<HttpClient>()
            every { httpClient.request(any()) } returns HttpClient.Response(200, "ok")
            val remoteConfigClient = TestRemoteConfigClient()

            val client =
                createClient(
                    httpClient = httpClient,
                    remoteConfigClient = remoteConfigClient,
                    enabled = true,
                    sampleRate = 0.0,
                    testScope = this,
                )
            testScheduler.runCurrent()

            remoteConfigClient.emit(mapOf("enabled" to "true", "sampleRate" to "1.0"))
            testScheduler.runCurrent()

            client.recordEvent("event", null)
            client.flush()

            verify(exactly = 0) { httpClient.request(any()) }
        }

    private fun createClient(
        httpClient: HttpClient,
        remoteConfigClient: RemoteConfigClient,
        enabled: Boolean,
        sampleRate: Double,
        testScope: TestScope,
    ): DiagnosticsClientImpl {
        val logger = mockk<Logger>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScope.testScheduler)
        val storageDir = createTempDir(prefix = "diag-remote-config")
        return DiagnosticsClientImpl(
            apiKey = "test-api-key",
            serverZone = ServerZone.US,
            instanceName = "test-instance",
            storageDirectory = storageDir,
            logger = logger,
            coroutineScope = testScope,
            networkIODispatcher = dispatcher,
            storageIODispatcher = dispatcher,
            remoteConfigClient = remoteConfigClient,
            httpClient = httpClient,
            contextProvider = null,
            enabled = enabled,
            sampleRate = sampleRate,
            flushIntervalMillis = 60_000,
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
