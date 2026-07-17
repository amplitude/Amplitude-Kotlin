package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.ServerZone
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class DiagnosticsClientTest {
    @Test
    fun `flush uploads payload with tags counters histograms and events`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client =
                createClient(
                    httpClient = httpClient,
                    sampleRate = 1.0,
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

            delay(100) // Allow actor to process initialization

            client.setTag("single_tag", "single_value")
            client.increment("counter_1", 5)
            client.recordHistogram("hist_1", 3.0)
            client.recordEvent("event_a", mapOf("k" to "v"))
            client.flush()

            delay(200) // Allow actor to process all messages and complete the flush

            verify(atLeast = 1) { httpClient.request(any()) }

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
            assertTrue(events.length() >= 1)
            // Find the event_a event in the array
            var foundEvent = false
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                if (event.getString("event_name") == "event_a") {
                    val eventProps = event.getJSONObject("event_properties")
                    assertEquals("v", eventProps.getString("k"))
                    foundEvent = true
                    break
                }
            }
            assertTrue(foundEvent, "Expected to find event_a in events")
        }

    @Test
    fun `flush does not upload when sampled out`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            val client = createClient(httpClient = httpClient, sampleRate = 0.0)

            delay(100)

            client.setTag("single_tag", "single_value")
            client.flush()

            delay(200)

            verify(exactly = 0) { httpClient.request(any()) }
        }

    @Test
    fun `increment accumulates values`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            client.increment("counter", 5)
            client.increment("counter", 3)
            client.flush()

            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }

            val body = requestSlot.captured.body
            assertNotNull(body)
            val json = JSONObject(body)
            val counters = json.getJSONObject("counters")
            assertEquals(8, counters.getLong("counter"))
        }

    @Test
    fun `recordHistogram tracks statistics`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            client.recordHistogram("metric", 10.0)
            client.recordHistogram("metric", 5.0)
            client.recordHistogram("metric", 20.0)
            client.flush()

            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }

            val body = requestSlot.captured.body
            assertNotNull(body)
            val json = JSONObject(body)
            val histograms = json.getJSONObject("histogram")
            val histogram = histograms.getJSONObject("metric")
            assertEquals(3, histogram.getLong("count"))
            assertEquals(5.0, histogram.getDouble("min"))
            assertEquals(20.0, histogram.getDouble("max"))
            // avg = 35 / 3 = 11.666...
            assertEquals(35.0 / 3.0, histogram.getDouble("avg"), 0.001)
        }

    @Test
    fun `setTags merges multiple tags`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            client.setTag("tag1", "value1")
            client.setTags(mapOf("tag2" to "value2", "tag1" to "overwritten"))
            client.increment("counter", 1) // Need at least one metric to trigger upload
            client.flush()

            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }

            val body = requestSlot.captured.body
            assertNotNull(body)
            val json = JSONObject(body)
            val tags = json.getJSONObject("tags")
            assertEquals("overwritten", tags.getString("tag1"))
            assertEquals("value2", tags.getString("tag2"))
        }

    @Test
    fun `flush clears counters histograms and events`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            client.increment("counter", 10)
            client.recordHistogram("metric", 5.0)
            client.recordEvent("event", null)
            client.flush()

            delay(200)

            // First flush should have data
            verify(atLeast = 1) { httpClient.request(any()) }
            val firstBody = requestSlot.captured.body
            assertNotNull(firstBody)
            val firstJson = JSONObject(firstBody)
            assertTrue(firstJson.has("counters"))

            // Record new data and flush again
            client.increment("new_counter", 1)
            client.flush()

            delay(200)

            // Second flush should only have new data, not the old
            val secondBody = requestSlot.captured.body
            assertNotNull(secondBody)
            val secondJson = JSONObject(secondBody)
            val counters = secondJson.getJSONObject("counters")
            assertEquals(1, counters.getLong("new_counter"))
            // Old counter should not be present
            assertTrue(!counters.has("counter") || counters.getLong("counter") == 0L)
        }

    @Test
    fun `flush does nothing when disabled`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(any())
            } returns HttpClient.Response(200, "ok")

            val client =
                createClient(
                    httpClient = httpClient,
                    sampleRate = 1.0,
                    contextProvider = null,
                    enabled = false,
                )

            delay(100)

            client.flush()

            delay(200)

            verify(exactly = 0) { httpClient.request(any()) }
        }

    @Test
    fun `uses EU diagnostics URL for EU server zone`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client =
                createClient(
                    httpClient = httpClient,
                    sampleRate = 1.0,
                    serverZone = ServerZone.EU,
                )

            delay(100)

            client.increment("counter", 1)
            client.flush()

            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }
            assertTrue(requestSlot.captured.url.contains("eu-central-1"))
        }

    @Test
    fun `events are limited to max count per flush`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            // Record more than MAX_EVENT_COUNT_PER_FLUSH (10) events
            for (i in 1..15) {
                client.recordEvent("event_$i", null)
            }
            client.flush()

            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }

            val body = requestSlot.captured.body
            assertNotNull(body)
            val json = JSONObject(body)
            val events = json.getJSONArray("events")
            // Should be limited to 10 events
            assertTrue(events.length() <= 10)
        }

    @Test
    fun `recordEvent without properties creates event with no properties`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            client.recordEvent("simple_event", null)
            client.flush()

            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }

            val body = requestSlot.captured.body
            assertNotNull(body)
            val json = JSONObject(body)
            val events = json.getJSONArray("events")
            var foundEvent = false
            for (i in 0 until events.length()) {
                val event = events.getJSONObject(i)
                if (event.getString("event_name") == "simple_event") {
                    assertTrue(!event.has("event_properties") || event.isNull("event_properties"))
                    foundEvent = true
                    break
                }
            }
            assertTrue(foundEvent)
        }

    @Test
    fun `HTTP request includes correct headers`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            client.increment("counter", 1)
            client.flush()

            delay(200)

            verify(atLeast = 1) { httpClient.request(any()) }

            val headers = requestSlot.captured.headers
            assertEquals("test-api-key", headers["X-ApiKey"])
            assertEquals("1.0", headers["X-Client-Sample-Rate"])
        }

    @Test
    fun `HTTP error does not crash client`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(any())
            } returns HttpClient.Response(500, "Internal Server Error")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            client.increment("counter", 1)
            client.flush()

            delay(200)

            // Should not throw, just log error
            verify(atLeast = 1) { httpClient.request(any()) }

            // Client should still be functional after error
            client.increment("counter2", 1)
            client.flush()

            delay(200)

            verify(atLeast = 2) { httpClient.request(any()) }
        }

    @Test
    fun `tags persist across flushes while metrics are cleared`() =
        runBlocking {
            val requestSlot = slot<HttpClient.Request>()
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(capture(requestSlot))
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            client.setTag("persistent_tag", "value")
            client.increment("counter", 1)
            client.flush()

            delay(200)

            // First flush should have both tag and counter
            var body = requestSlot.captured.body
            assertNotNull(body)
            var json = JSONObject(body)
            assertTrue(json.has("tags"))
            assertEquals("value", json.getJSONObject("tags").getString("persistent_tag"))
            assertTrue(json.has("counters"))

            // Second flush with new counter - tag should persist
            client.increment("new_counter", 1)
            client.flush()

            delay(200)

            body = requestSlot.captured.body
            assertNotNull(body)
            json = JSONObject(body)
            // Tags should still be present
            assertTrue(json.has("tags"))
            assertEquals("value", json.getJSONObject("tags").getString("persistent_tag"))
        }

    @Test
    fun `sample rate is clamped to valid range`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(any())
            } returns HttpClient.Response(200, "ok")

            // Sample rate > 1.0 should be clamped to 1.0
            val client = createClient(httpClient = httpClient, sampleRate = 2.0)

            delay(100)

            client.increment("counter", 1)
            client.flush()

            delay(200)

            // Should upload since clamped sample rate of 1.0 means always sample
            verify(atLeast = 1) { httpClient.request(any()) }
        }

    @Test
    fun `flush does nothing when no data recorded`() =
        runBlocking {
            val httpClient = mockk<HttpClient>()
            every {
                httpClient.request(any())
            } returns HttpClient.Response(200, "ok")

            val client = createClient(httpClient = httpClient, sampleRate = 1.0)

            delay(100)

            // Flush without recording any data (other than initialization)
            // Note: initialization records "sampled.in.and.enabled" counter
            // So first flush will have data, but second flush without new data should not upload
            client.flush()
            delay(200)

            // Clear the verification
            io.mockk.clearMocks(httpClient, answers = false)
            every {
                httpClient.request(any())
            } returns HttpClient.Response(200, "ok")

            // Now flush again without adding any data
            client.flush()
            delay(200)

            verify(exactly = 0) { httpClient.request(any()) }
        }

    private fun createClient(
        httpClient: HttpClient,
        sampleRate: Double,
        contextProvider: DiagnosticsContextProvider? = null,
        enabled: Boolean = true,
        serverZone: ServerZone = ServerZone.US,
    ): DiagnosticsClientImpl {
        val logger = mockk<Logger>(relaxed = true)
        val storageDir = File.createTempFile("diagnostics", "test").parentFile

        return DiagnosticsClientImpl(
            apiKey = "test-api-key",
            serverZone = serverZone,
            instanceName = "test-instance",
            storageDirectory = storageDir,
            logger = logger,
            coroutineScope = CoroutineScope(Dispatchers.Default),
            networkIODispatcher = Dispatchers.IO,
            storageIODispatcher = Dispatchers.IO,
            remoteConfigClient = null,
            httpClient = httpClient,
            contextProvider = contextProvider,
            enabled = enabled,
            sampleRate = sampleRate,
            flushIntervalMillis = 60_000,
            storageIntervalMillis = 60_000,
        )
    }
}
