package com.amplitude.android.streaming.internal.network

import com.amplitude.android.streaming.internal.DelayedEvent
import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class DelayedEventsEndpointTest {
    private lateinit var server: MockWebServer
    private val logger = mockk<Logger>(relaxed = true)

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun shutdown() {
        server.shutdown()
    }

    @Nested
    inner class Responses {
        @Test
        fun `2xx is success and posts json to delayed`() =
            runTest {
                server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
                val result = send()
                assertEquals(DelayedEventsResult.Success, result)

                val recorded = server.takeRequest(1, TimeUnit.SECONDS)
                assertEquals("POST", recorded?.method)
                assertEquals("/2/httpapi/delayed", recorded?.path)
                assertEquals(
                    "application/json; charset=utf-8",
                    recorded?.getHeader("Content-Type"),
                )
                val body = JSONObject(recorded!!.body.readUtf8())
                assertEquals("test-api-key", body.getString("api_key"))
                assertEquals("view-1", body.getString("id"))
                assertEquals(5_000, body.getLong("timeout"))
            }

        @Test
        fun `204 is success`() =
            runTest {
                server.enqueue(MockResponse().setResponseCode(204))
                assertEquals(DelayedEventsResult.Success, send())
            }

        @Test
        fun `429 is rate limited`() =
            runTest {
                server.enqueue(MockResponse().setResponseCode(429))
                assertEquals(DelayedEventsResult.RateLimited, send())
            }

        @Test
        fun `5xx uses the response body as the failure message`() =
            runTest {
                server.enqueue(MockResponse().setResponseCode(500).setBody("upstream down"))
                val result = send()
                assertEquals(
                    DelayedEventsResult.Failure(statusCode = 500, message = "upstream down"),
                    result,
                )
            }
    }

    @Nested
    inner class Transport {
        @Test
        fun `connection failure is a failure without a status code`() =
            runTest {
                server.shutdown()
                val result = send()
                assertTrue(result is DelayedEventsResult.Failure)
                assertEquals(null, (result as DelayedEventsResult.Failure).statusCode)
            }
    }

    @Nested
    inner class Serialization {
        @Test
        fun `serializes contextual properties at top level of event`() =
            runTest {
                server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
                val configuration =
                    Configuration(
                        apiKey = "test-api-key",
                        serverUrl = server.url("/").toString(),
                    )
                val endpoint =
                    DelayedEventsEndpoint(
                        configuration = configuration,
                        amplitudeBaseUrl = AmplitudeBaseUrl(configuration),
                        logger = logger,
                        ioDispatcher = UnconfinedTestDispatcher(),
                    )

                val delayedEvent =
                    DelayedEvent(
                        eventType = "Video Content Stopped",
                        kind = DelayedEvent.Kind.DELAYED,
                        timestamp = 1_725_000_000_000L,
                        userId = "user-abc",
                        deviceId = "device-xyz",
                        sessionId = 999L,
                        eventProperties = mutableMapOf("video_id" to "v-100"),
                    ).apply {
                        insertId = "insert-123"
                        eventId = 42L
                        library = "amplitude-android/1.0.0"
                        appVersion = "2.3.4"
                        versionName = "2.3.4-prod"
                        platform = "Android"
                        osName = "android"
                        osVersion = "14"
                        deviceBrand = "Google"
                        deviceManufacturer = "Google"
                        deviceModel = "Pixel 8"
                        carrier = "T-Mobile"
                        country = "US"
                        region = "California"
                        city = "San Francisco"
                        dma = "San Francisco-Oakland-San Jose"
                        language = "en-US"
                        ip = "127.0.0.1"
                        locationLat = 37.7749
                        locationLng = -122.4194
                        idfa = "idfa-1"
                        idfv = "idfv-1"
                        adid = "adid-1"
                        androidId = "android-id-1"
                        appSetId = "app-set-1"
                        partnerId = "partner-1"
                        userProperties = mutableMapOf("plan" to "premium")
                        groups = mutableMapOf("org" to "amplitude")
                        groupProperties = mutableMapOf("group_plan" to "enterprise")
                    }

                val result =
                    endpoint.send(
                        DelayedEventsRequestDto(
                            id = "view-ctx",
                            timeoutMillis = 5_000,
                            events = listOf(delayedEvent),
                        ),
                    )

                assertEquals(DelayedEventsResult.Success, result)
                val recorded = server.takeRequest(1, TimeUnit.SECONDS)
                val body = JSONObject(recorded!!.body.readUtf8())
                val event = body.getJSONArray("events").getJSONObject(0)

                assertEquals("Video Content Stopped", event.getString("event_type"))
                assertEquals(1_725_000_000_000L, event.getLong("time"))
                assertEquals("user-abc", event.getString("user_id"))
                assertEquals("device-xyz", event.getString("device_id"))
                assertEquals(999L, event.getLong("session_id"))
                assertEquals("insert-123", event.getString("insert_id"))
                assertEquals(42L, event.getLong("event_id"))
                assertEquals("amplitude-android/1.0.0", event.getString("library"))
                assertEquals("2.3.4", event.getString("app_version"))
                assertEquals("2.3.4-prod", event.getString("version_name"))
                assertEquals("Android", event.getString("platform"))
                assertEquals("android", event.getString("os_name"))
                assertEquals("14", event.getString("os_version"))
                assertEquals("Google", event.getString("device_brand"))
                assertEquals("Google", event.getString("device_manufacturer"))
                assertEquals("Pixel 8", event.getString("device_model"))
                assertEquals("T-Mobile", event.getString("carrier"))
                assertEquals("US", event.getString("country"))
                assertEquals("California", event.getString("region"))
                assertEquals("San Francisco", event.getString("city"))
                assertEquals("San Francisco-Oakland-San Jose", event.getString("dma"))
                assertEquals("en-US", event.getString("language"))
                assertEquals("127.0.0.1", event.getString("ip"))
                assertEquals(37.7749, event.getDouble("location_lat"))
                assertEquals(-122.4194, event.getDouble("location_lng"))
                assertEquals("idfa-1", event.getString("idfa"))
                assertEquals("idfv-1", event.getString("idfv"))
                assertEquals("adid-1", event.getString("adid"))
                assertEquals("android-id-1", event.getString("android_id"))
                assertEquals("app-set-1", event.getString("android_app_set_id"))
                assertEquals("partner-1", event.getString("partner_id"))
                assertEquals("premium", event.getJSONObject("user_properties").getString("plan"))
                assertEquals("amplitude", event.getJSONObject("groups").getString("org"))
                assertEquals("enterprise", event.getJSONObject("group_properties").getString("group_plan"))
                assertEquals("v-100", event.getJSONObject("event_properties").getString("video_id"))
                assertFalse(body.has("instant_events"))
            }

        @Test
        fun `serializes instant_events and zero timeout on flush`() =
            runTest {
                server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
                val configuration =
                    Configuration(
                        apiKey = "test-api-key",
                        serverUrl = server.url("/").toString(),
                    )
                val endpoint =
                    DelayedEventsEndpoint(
                        configuration = configuration,
                        amplitudeBaseUrl = AmplitudeBaseUrl(configuration),
                        logger = logger,
                        ioDispatcher = UnconfinedTestDispatcher(),
                    )

                val result =
                    endpoint.send(
                        DelayedEventsRequestDto(
                            id = "view-flush",
                            timeoutMillis = 0L,
                            events =
                                listOf(
                                    DelayedEvent(
                                        eventType = "Video Content Stopped",
                                        kind = DelayedEvent.Kind.DELAYED,
                                        timestamp = 100L,
                                    ),
                                ),
                            instantEvents =
                                listOf(
                                    DelayedEvent(
                                        eventType = "Video Content Playing",
                                        kind = DelayedEvent.Kind.INSTANT,
                                        timestamp = 101L,
                                    ),
                                ),
                        ),
                    )

                assertEquals(DelayedEventsResult.Success, result)
                val recorded = server.takeRequest(1, TimeUnit.SECONDS)
                val body = JSONObject(recorded!!.body.readUtf8())
                assertEquals(0L, body.getLong("timeout"))
                assertEquals(1, body.getJSONArray("events").length())
                assertTrue(body.has("instant_events"))
                val instantEvents = body.getJSONArray("instant_events")
                assertEquals(1, instantEvents.length())
                assertEquals("Video Content Playing", instantEvents.getJSONObject(0).getString("event_type"))
                assertEquals(101L, instantEvents.getJSONObject(0).getLong("time"))
            }
    }

    private suspend fun send(): DelayedEventsResult {
        val configuration =
            Configuration(
                apiKey = "test-api-key",
                serverUrl = server.url("/").toString(),
            )
        return DelayedEventsEndpoint(
            configuration = configuration,
            amplitudeBaseUrl = AmplitudeBaseUrl(configuration),
            logger = logger,
            ioDispatcher = UnconfinedTestDispatcher(),
        ).send(
            DelayedEventsRequestDto(
                id = "view-1",
                timeoutMillis = 5_000,
                events =
                    listOf(
                        DelayedEvent(
                            eventType = "Video Content Stopped",
                            kind = DelayedEvent.Kind.DELAYED,
                            timestamp = 1L,
                        ),
                    ),
            ),
        )
    }
}
