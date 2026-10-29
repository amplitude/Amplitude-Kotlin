@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.core.diagnostics

import com.amplitude.common.Logger
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.coroutines.CoroutineContext

class DiagnosticsActorInitializationTest {
    @TempDir
    lateinit var directory: File

    @Test
    fun `constructor and early calls do not resolve scope or start subscription`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val parent = backgroundScope
        var contextReads = 0
        val scope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext
                get() {
                    contextReads += 1
                    return parent.coroutineContext
                }
        }
        val remote = mockk<RemoteConfigClient>(relaxed = true)
        val contextProvider = mockk<DiagnosticsContextProvider>(relaxed = true)
        val client = createClient(
            dispatcher = dispatcher,
            scope = scope,
            remote = remote,
            contextProvider = contextProvider,
        )
        client.setTag(name = "early", value = "tag")
        client.increment(name = "early", size = 2)

        assertEquals(0, contextReads)
        verify(exactly = 0) { remote.subscribe(any(), any(), any()) }
        verify(exactly = 0) { contextProvider.getContextInfo() }
        runCurrent()
        assertTrue(contextReads > 0)
        verify(exactly = 1) { remote.subscribe(RemoteConfigClient.Key.Diagnostics, any(), any()) }
        verify(exactly = 1) { contextProvider.getContextInfo() }
        client.close()
        runCurrent()
    }

    @Test
    fun `early tag and event snapshots preserve timestamps and command order`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = SupervisorJob()
        val http = mockk<HttpClient>()
        val request = slot<HttpClient.Request>()
        every { http.request(capture(request)) } returns HttpClient.Response(200, "ok")
        val client = createClient(
            dispatcher = dispatcher,
            scope = CoroutineScope(owner + dispatcher),
            http = http,
            onClosed = { owner.complete() },
        )
        try {
            val tags = mutableMapOf("early" to "first")
            val nested = mutableMapOf<String, Any>("value" to "before")
            val items = mutableListOf<Any>(nested)
            val properties = mutableMapOf<String, Any>("items" to items)
            val before = System.currentTimeMillis() / 1000.0
            client.setTags(tags = tags)
            client.setTag(name = "early", value = "last")
            client.recordEvent(name = "early event", properties = properties)
            val after = System.currentTimeMillis() / 1000.0
            tags["early"] = "mutated"
            nested["value"] = "after"
            items.clear()
            properties.clear()
            client.flush()
            client.close()
            advanceUntilIdle()

            verify(exactly = 1) { http.request(any()) }
            val payload = JSONObject(request.captured.body?.joinToString(separator = ""))
            assertEquals("last", payload.getJSONObject("tags").getString("early"))
            val event = payload.getJSONArray("events").getJSONObject(0)
            assertEquals("early event", event.getString("event_name"))
            assertTrue(event.getDouble("time") in before..after)
            assertEquals("before", event.getJSONObject("event_properties").getJSONArray("items").getJSONObject(0).getString("value"))
            assertTrue(owner.isCompleted)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun `initial synchronous disable takes effect before an early flush`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = SupervisorJob()
        val http = mockk<HttpClient>(relaxed = true)
        val remote = mockk<RemoteConfigClient>()
        every { remote.subscribe(any(), any(), any()) } answers {
            thirdArg<RemoteConfigClient.RemoteConfigCallback>().onUpdate(
                config = mapOf("enabled" to false),
                source = RemoteConfigClient.Source.CACHE,
                timestamp = 1,
            )
        }
        val client = createClient(
            dispatcher = dispatcher,
            scope = CoroutineScope(owner + dispatcher),
            remote = remote,
            http = http,
            onClosed = { owner.complete() },
        )
        try {
            client.increment(name = "early", size = 1)
            client.flush()
            client.close()
            advanceUntilIdle()
            verify(exactly = 0) { http.request(any()) }
            assertTrue(owner.isCompleted)
        } finally {
            owner.cancel()
        }
    }

    @Test
    fun `close before startup skips initialization and closes once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val parent = backgroundScope
        var contextReads = 0
        var closes = 0
        val scope = object : CoroutineScope {
            override val coroutineContext: CoroutineContext
                get() {
                    contextReads += 1
                    return parent.coroutineContext
                }
        }
        val remote = mockk<RemoteConfigClient>(relaxed = true)
        val client = createClient(
            dispatcher = dispatcher,
            scope = scope,
            remote = remote,
            onClosed = { closes += 1 },
        )
        client.close()
        client.close()
        client.increment(name = "too late", size = 1)
        advanceUntilIdle()

        assertEquals(0, contextReads)
        assertEquals(1, closes)
        verify(exactly = 0) { remote.subscribe(any(), any(), any()) }
    }

    @Test
    fun `configuration callbacks are inactive after actor closes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = SupervisorJob()
        val logger = mockk<Logger>(relaxed = true)
        val remote = mockk<RemoteConfigClient>()
        val callback = slot<RemoteConfigClient.RemoteConfigCallback>()
        every { remote.subscribe(any(), any(), capture(callback)) } returns Unit
        val client = DiagnosticsClientImpl(
            apiKey = "test-api-key",
            serverZone = ServerZone.US,
            instanceName = "test",
            storageDirectory = directory,
            logger = logger,
            coroutineScope = CoroutineScope(owner + dispatcher),
            networkIODispatcher = dispatcher,
            storageIODispatcher = dispatcher,
            remoteConfigClient = remote,
            httpClient = mockk(relaxed = true),
            onClosed = { owner.complete() },
        )
        try {
            runCurrent()
            client.close()
            advanceUntilIdle()
            clearMocks(logger, answers = false)
            callback.captured.onUpdate(
                config = mapOf("enabled" to true, "sampleRate" to 1.0),
                source = RemoteConfigClient.Source.REMOTE,
                timestamp = 2,
            )
            runCurrent()
            assertTrue(owner.isCompleted)
            verify(exactly = 0) { logger.debug(any()) }
        } finally {
            owner.cancel()
        }
    }

    private fun createClient(
        dispatcher: CoroutineDispatcher,
        scope: CoroutineScope,
        remote: RemoteConfigClient? = null,
        contextProvider: DiagnosticsContextProvider? = null,
        http: HttpClient = mockk(relaxed = true),
        onClosed: () -> Unit = {},
    ): DiagnosticsClientImpl = DiagnosticsClientImpl(
        apiKey = "test-api-key",
        serverZone = ServerZone.US,
        instanceName = "test",
        storageDirectory = directory,
        logger = mockk<Logger>(relaxed = true),
        coroutineScope = scope,
        networkIODispatcher = dispatcher,
        storageIODispatcher = dispatcher,
        remoteConfigClient = remote,
        httpClient = http,
        contextProvider = contextProvider,
        sampleRate = 1.0,
        onClosed = onClosed,
    )
}
