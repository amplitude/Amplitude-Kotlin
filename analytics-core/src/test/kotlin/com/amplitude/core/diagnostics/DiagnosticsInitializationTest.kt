@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.Constants
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DiagnosticsInitializationTest {
    @Test
    fun `early flush uses initial remote configuration and drains owned scope on close`(
        @TempDir directory: File,
    ) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + dispatcher)
        val logger = mockk<Logger>(relaxed = true)
        val http = mockk<HttpClient>()
        val request = slot<HttpClient.Request>()
        every { http.request(capture(request)) } returns HttpClient.Response(200, "ok")
        val remote = mockk<RemoteConfigClient>()
        every { remote.subscribe(any(), any(), any()) } answers {
            thirdArg<RemoteConfigClient.RemoteConfigCallback>().onUpdate(
                config = mapOf("enabled" to true, "sampleRate" to 1.0),
                source = RemoteConfigClient.Source.CACHE,
                timestamp = 1,
            )
        }
        val client = DiagnosticsClientImpl(
            apiKey = "test-api-key",
            serverZone = ServerZone.US,
            instanceName = "test",
            storageDirectory = directory,
            logger = logger,
            coroutineScope = scope,
            networkIODispatcher = dispatcher,
            storageIODispatcher = dispatcher,
            remoteConfigClient = remote,
            httpClient = http,
            enabled = false,
            sampleRate = 0.0,
            onClosed = { owner.complete() },
        )
        try {
            client.recordEvent(name = "before initialization", properties = mapOf("key" to "value"))
            client.increment(name = "early counter", size = 2)
            client.flush()
            client.close()
            advanceUntilIdle()

            verify(exactly = 1) { http.request(any()) }
            val payload = JSONObject(request.captured.body?.joinToString(separator = ""))
            assertEquals(2, payload.getJSONObject("counters").getLong("early counter"))
            assertEquals("before initialization", payload.getJSONArray("events").getJSONObject(0).getString("event_name"))
            assertTrue(payload.getJSONObject("tags").has("sdk.${Constants.SDK_LIBRARY}.version"))
            assertEquals("1.0", request.captured.headers?.get("X-Client-Sample-Rate"))
            assertTrue(owner.isCompleted, "Close must drain and release diagnostics, storage, and upload workers")
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun `close with queued work drains initialization and releases a newly created storage actor`(
        @TempDir directory: File,
    ) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = SupervisorJob()
        val scope = CoroutineScope(owner + dispatcher)
        val http = mockk<HttpClient>(relaxed = true)
        val client = DiagnosticsClientImpl(
            apiKey = "test-api-key",
            serverZone = ServerZone.US,
            instanceName = "test",
            storageDirectory = directory,
            logger = mockk<Logger>(relaxed = true),
            coroutineScope = scope,
            networkIODispatcher = dispatcher,
            storageIODispatcher = dispatcher,
            httpClient = http,
            sampleRate = 0.0,
            onClosed = { owner.complete() },
        )
        try {
            client.setTag(name = "queued", value = "before close")
            client.close()
            advanceUntilIdle()
            assertTrue(owner.isCompleted)
        } finally {
            owner.cancel()
        }
    }
}
