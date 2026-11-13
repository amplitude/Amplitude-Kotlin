package com.amplitude.android.streaming.internal

import android.content.Context
import com.amplitude.android.Configuration
import com.amplitude.android.streaming.internal.storage.DelayedEventsRequestEntity
import com.amplitude.android.streaming.internal.storage.delayedEventStorage
import com.amplitude.android.streaming.internal.storage.queueFileExists
import com.amplitude.android.streaming.internal.storage.toEntity
import com.amplitude.core.AmplitudePreview
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import com.amplitude.android.Amplitude as AndroidAmplitude

@OptIn(AmplitudePreview::class, ExperimentalCoroutinesApi::class)
class StreamingAnalyticsTest {
    private lateinit var server: MockWebServer
    private lateinit var amplitudeDir: File
    private lateinit var context: Context
    private var streamingAnalytics: StreamingAnalytics? = null

    @BeforeEach
    fun setUp() {
        mockkStatic(::queueFileExists)
        every { queueFileExists(any()) } answers { java.nio.file.Files.exists(firstArg<File>().toPath()) }
        server = MockWebServer()
        server.start()
        amplitudeDir = createTempDirectory("amplitude").toFile()
        context =
            mockk {
                every { getDir("amplitude", Context.MODE_PRIVATE) } returns amplitudeDir
                every { packageName } returns "com.example.app"
                every { applicationContext } returns this
            }
    }

    @AfterEach
    fun tearDown() {
        streamingAnalytics?.teardown()
        server.shutdown()
        amplitudeDir.deleteRecursively()
        unmockkStatic(::queueFileExists)
    }

    @Test
    fun `startup drains persisted delayed events from previous session`() {
        val configuration =
            Configuration(
                apiKey = "test-api-key",
                context = context,
                instanceName = "default_instance",
                serverUrl = server.url("/").toString(),
            )
        val amplitude = mockk<AndroidAmplitude>(relaxed = true)
        every { amplitude.configuration } returns configuration
        every { amplitude.logger } returns mockk(relaxed = true)
        every { amplitude.amplitudeScope } returns CoroutineScope(SupervisorJob())

        val graph = StreamingDiGraph(amplitude)
        val storage = graph.delayedEventStorage

        // Write an event from a "previous session"
        val request =
            DelayedEventsRequestEntity(
                id = "persisted-stream-1",
                timeoutMillis = 5_000L,
                events =
                    listOf(
                        DelayedEvent(
                            eventType = "Video Content Stopped",
                            kind = DelayedEvent.Kind.DELAYED,
                            timestamp = 1L,
                            eventProperties = mutableMapOf("video_id" to "v-100"),
                        ).toEntity(),
                    ),
            )
        kotlinx.coroutines.runBlocking {
            storage.write("0000000000000000001-persisted", request)
        }

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        streamingAnalytics = StreamingAnalytics(amplitude)

        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recorded, "Expected startup drain to send delayed events to the server")
        assertEquals("/delayed", recorded?.path)

        var keys = kotlinx.coroutines.runBlocking { storage.keys() }
        val deadline = System.currentTimeMillis() + 5_000L
        while (keys.isNotEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            keys = kotlinx.coroutines.runBlocking { storage.keys() }
        }
        assertEquals(emptyList<String>(), keys)
    }

    @Test
    fun `should upload a clean stop immediately after the initial fallback`() {
        val configuration = Configuration(apiKey = "test-key", context = context, serverUrl = server.url("/").toString())
        val amplitude = mockk<AndroidAmplitude>(relaxed = true)
        every { amplitude.configuration } returns configuration
        every { amplitude.logger } returns mockk(relaxed = true)
        every { amplitude.amplitudeScope } returns CoroutineScope(SupervisorJob())
        val streaming = StreamingAnalytics(amplitude).also { streamingAnalytics = it }

        fun event(kind: DelayedEvent.Kind, start: Boolean = false) =
            DelayedEvent(
                eventType = if (start) StreamEvents.STREAM_STARTED else StreamEvents.STREAM_STOPPED,
                kind = kind,
                timestamp = if (kind == DelayedEvent.Kind.INSTANT && !start) 10_000L else 0L,
                eventProperties =
                    mutableMapOf(
                        "[Streaming] Stream Session ID" to "stream-1",
                        "[Streaming] Play Time Sec" to if (kind == DelayedEvent.Kind.INSTANT && !start) 10.0 else 0.0,
                        "[Streaming] Stop Reason" to if (kind == DelayedEvent.Kind.INSTANT && !start) "paused" else "timeout",
                    ),
            ).also { it.insertId = if (start) "start-1" else "stop-1" }
        val batch = BatchInfo(UUID.randomUUID(), 2)
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        streaming.onDelayedEvent(DelayedEvent(event(DelayedEvent.Kind.INSTANT, start = true), DelayedEvent.Kind.INSTANT, batch))
        streaming.onDelayedEvent(DelayedEvent(event(DelayedEvent.Kind.DELAYED), DelayedEvent.Kind.DELAYED, batch))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS), "Expected the initial fallback upload")

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        streaming.onDelayedEvent(event(DelayedEvent.Kind.INSTANT))

        val recorded = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recorded, "Clean stops must upload before the 60-second heartbeat pulse")
        val request = JSONObject(recorded!!.body.readUtf8())
        assertEquals(0L, request.getLong("timeout"))
        assertEquals(0, request.getJSONArray("events").length())
        val stop = request.getJSONArray("instant_events").getJSONObject(0)
        assertEquals("stop-1", stop.getString("insert_id"))
        assertEquals("paused", stop.getJSONObject("event_properties").getString("[Streaming] Stop Reason"))
        assertEquals(10.0, stop.getJSONObject("event_properties").getDouble("[Streaming] Play Time Sec"))
    }

    @Test
    fun `should complete batches inside the single event handler`() =
        runTest {
            val configuration = Configuration(apiKey = "test-key", context = context, serverUrl = server.url("/").toString())
            val amplitude = mockk<AndroidAmplitude>(relaxed = true)
            every { amplitude.configuration } returns configuration
            every { amplitude.logger } returns mockk(relaxed = true)
            every { amplitude.amplitudeScope } returns CoroutineScope(SupervisorJob())
            val streaming = StreamingAnalytics(amplitude).also { streamingAnalytics = it }

            fun event(start: Boolean) =
                DelayedEvent(
                    eventType = if (start) StreamEvents.STREAM_STARTED else StreamEvents.STREAM_STOPPED,
                    kind = if (start) DelayedEvent.Kind.INSTANT else DelayedEvent.Kind.DELAYED,
                    timestamp = 1L,
                    eventProperties = mutableMapOf("[Streaming] Stream Session ID" to "stream-1"),
                ).also { it.insertId = if (start) "start-1" else "stop-1" }
            val batchInfo = BatchInfo(UUID.randomUUID(), 2)
            val (start, stop) = listOf(event(true), event(false)).map { DelayedEvent(it, it.kind, batchInfo) }

            streaming.onDelayedEvent(start)
            assertEquals(0, server.requestCount)

            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            streaming.onDelayedEvent(stop)
            val recorded = server.takeRequest(5, TimeUnit.SECONDS)
            assertNotNull(recorded)
            val request = JSONObject(recorded!!.body.readUtf8())
            assertEquals(1, request.getJSONArray("instant_events").length())
            assertEquals(1, request.getJSONArray("events").length())
            assertEquals("start-1", request.getJSONArray("instant_events").getJSONObject(0).getString("insert_id"))
            assertEquals("stop-1", request.getJSONArray("events").getJSONObject(0).getString("insert_id"))
            assertEquals(3_600_000L, request.getLong("timeout"))
        }
}
