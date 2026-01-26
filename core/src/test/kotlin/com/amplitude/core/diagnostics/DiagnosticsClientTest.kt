package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.ServerZone
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DiagnosticsClientTest {
    @Test
    fun `flush uploads payload with tags counters histograms and events`() =
        runTest {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns
                HttpClient.Response(
                    200,
                    "ok",
                )

            val client =
                createClient(
                    httpClient = httpClient,
                    sampleRate = 1.0,
                    testScope = this,
                    contextProvider =
                        DiagnosticsContextProvider {
                            DiagnosticsContextInfo(
                                manufacturer = "Google",
                                model = "Pixel",
                                osName = "Android",
                                osVersion = "14",
                                platform = "Android",
                                appVersion = "1.2.3",
                            )
                        },
                )

            client.setTag("single_tag", "single_value")
            client.increment("counter_1", 5)
            client.recordHistogram("hist_1", 3.0)
            client.recordEvent("event_a", mapOf("k" to "v"))
            client.flush()

            verify(exactly = 1) { httpClient.request(any()) }

            val body = requestSlot.captured.body
            assertNotNull(body)
            val json = JSONObject(body)

            val tags = json.getJSONObject("tags")
            assertEquals("single_value", tags.getString("single_tag"))

            val counters = json.getJSONObject("counters")
            assertEquals(5, counters.getLong("counter_1"))

            val histograms = json.getJSONObject("histogram")
            val histogram = histograms.getJSONObject("hist_1")
            assertEquals(1, histogram.getLong("count"))
            assertEquals(3.0, histogram.getDouble("min"))
            assertEquals(3.0, histogram.getDouble("max"))
            assertEquals(3.0, histogram.getDouble("avg"))

            val events = json.getJSONArray("events")
            assertTrue(events.length() == 1)
            val event = events.getJSONObject(0)
            assertEquals("event_a", event.getString("event_name"))
            val eventProps = event.getJSONObject("event_properties")
            assertEquals("v", eventProps.getString("k"))
        }

    @Test
    fun `flush does not upload when sampled out`() =
        runTest {
            val httpClient = mockk<HttpClient>()
            val client = createClient(httpClient = httpClient, sampleRate = 0.0, testScope = this)

            client.setTag("single_tag", "single_value")
            client.flush()

            verify(exactly = 0) { httpClient.request(any()) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createClient(
        httpClient: HttpClient,
        sampleRate: Double,
        testScope: TestScope,
        contextProvider: DiagnosticsContextProvider? = null,
    ): DiagnosticsClientImpl {
        val logger = mockk<Logger>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScope.testScheduler)
        val storageDir =
            File.createTempFile("diagnostics", "test").parentFile

        return DiagnosticsClientImpl(
            apiKey = "test-api-key",
            serverZone = ServerZone.US,
            instanceName = "test-instance",
            storageDirectory = storageDir,
            logger = logger,
            coroutineScope = testScope,
            networkIODispatcher = dispatcher,
            storageIODispatcher = dispatcher,
            remoteConfigClient = null,
            httpClient = httpClient,
            contextProvider = contextProvider,
            enabled = true,
            sampleRate = sampleRate,
            flushIntervalMillis = 1,
        )
    }
}
