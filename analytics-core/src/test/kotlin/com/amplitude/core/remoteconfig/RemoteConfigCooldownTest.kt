@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.core.remoteconfig

import com.amplitude.common.Logger
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.ServerZone
import com.amplitude.core.remoteconfig.RemoteConfigClient.DeliveryMode
import com.amplitude.core.remoteconfig.RemoteConfigClient.Key
import com.amplitude.core.remoteconfig.RemoteConfigClient.Source
import com.amplitude.core.utilities.Time
import com.amplitude.core.utilities.http.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteConfigCooldownTest {
    @TempDir
    lateinit var directory: File

    private var now = 2_000_000L
    private val time = mockk<Time> { every { nowMillis() } answers { now } }
    private val logger = mockk<Logger>(relaxed = true)
    private val http = mockk<HttpClient>()
    private val callbacks = mutableListOf<RemoteConfigClient.RemoteConfigCallback>()
    private val success = HttpClient.Response(200, """{"configs":{"sessionReplay":{"enabled":true}}}""")

    init {
        every { http.request(any()) } returns success
    }

    @Nested
    inner class PersistedSuccess {
        @ParameterizedTest
        @ValueSource(booleans = [false, true])
        fun `should reuse a persisted success on startup until the original cooldown expires`(waitForRemote: Boolean) =
            runTest {
                val timestamp = now
                client(this, fileStorage()).deliveries()
                advanceUntilIdle()

                now += 15 * 60 * 1_000L - 1
                // Both the client and storage are new: only the on-disk cache can answer.
                val restarted = client(this, fileStorage())
                val deliveries = restarted.deliveries(mode(waitForRemote))
                restarted.updateConfigs()
                advanceUntilIdle()

                verify(exactly = 1) { http.request(any()) }
                assertEquals(Triple(mapOf("enabled" to true), Source.CACHE, timestamp), deliveries.single())

                // Cache reuse must not restart the 15-minute window.
                now += 1
                repeat(3) { restarted.updateConfigs() }
                advanceUntilIdle()
                verify(exactly = 2) { http.request(any()) }
                assertEquals(listOf(Source.CACHE, Source.REMOTE), deliveries.map { it.second })
                assertEquals(now, deliveries.last().third)
            }

        @ParameterizedTest
        @ValueSource(booleans = [false, true])
        fun `should fetch on startup when the persisted success has expired`(waitForRemote: Boolean) =
            runTest {
                client(this, fileStorage()).deliveries()
                advanceUntilIdle()
                now += 15 * 60 * 1_000L + 1

                val deliveries = client(this, fileStorage()).deliveries(mode(waitForRemote))
                advanceUntilIdle()

                verify(exactly = 2) { http.request(any()) }
                val expectedSources = if (waitForRemote) listOf(Source.REMOTE) else listOf(Source.CACHE, Source.REMOTE)
                assertEquals(expectedSources, deliveries.map { it.second })
                assertEquals(now, deliveries.last().third)
            }

        @ParameterizedTest
        @ValueSource(strings = ["{}", """{"sessionReplay":{}}"""])
        fun `should reuse a fresh empty or missing key without fetching`(config: String) =
            runTest {
                every { http.request(any()) } returns HttpClient.Response(200, """{"configs":$config}""")
                client(this, fileStorage()).deliveries(key = Key.Diagnostics)
                advanceUntilIdle()

                listOf(DeliveryMode.All, DeliveryMode.WaitForRemote(1_000L)).forEach { mode ->
                    val deliveries = client(this, fileStorage()).deliveries(mode, Key.Diagnostics)
                    advanceUntilIdle()
                    assertEquals(Triple(null, Source.CACHE, now), deliveries.single())
                }
                verify(exactly = 1) { http.request(any()) }
            }

        @ParameterizedTest
        @ValueSource(strings = ["{broken", """{"sessionReplay.sr_android_privacy_config":{}}"""])
        fun `should fetch when a recent timestamp has no usable cached config`(config: String) =
            runTest {
                val storage = TestStorage(CachedRemoteConfig(config, now))
                val deliveries = client(this, storage).deliveries()
                advanceUntilIdle()

                verify(exactly = 1) { http.request(any()) }
                assertEquals(Triple(mapOf("enabled" to true), Source.REMOTE, now), deliveries.single())
            }

        @Test
        fun `should allow startup to retry after a failed fetch`() =
            runTest {
                every { http.request(any()) } returns HttpClient.Response(500, null, isTransportError = true)
                val failed = client(this, fileStorage()).deliveries()
                advanceUntilIdle()
                assertNull(failed.single().third)

                every { http.request(any()) } returns success
                val recovered = client(this, fileStorage()).deliveries()
                advanceUntilIdle()

                verify(exactly = 2) { http.request(any()) }
                assertEquals(Triple(mapOf("enabled" to true), Source.REMOTE, now), recovered.single())
            }
    }

    @Nested
    inner class InMemorySuccess {
        @ParameterizedTest
        @ValueSource(booleans = [false, true])
        fun `should honor a successful fetch when storage writes or later reads fail`(failWrites: Boolean) =
            runTest {
                val storage = TestStorage().apply { this.failWrites = failWrites }
                val client = client(this, storage)
                client.deliveries()
                advanceUntilIdle()
                storage.failReads = !failWrites

                now += 15 * 60 * 1_000L - 1
                repeat(3) { client.updateConfigs() }
                val later = client.deliveries()
                advanceUntilIdle()
                verify(exactly = 1) { http.request(any()) }
                assertEquals(Source.REMOTE, later.single().second)

                now += 1
                repeat(3) { client.updateConfigs() }
                advanceUntilIdle()
                verify(exactly = 2) { http.request(any()) }
            }

        @Test
        fun `should recheck the cooldown when a concurrent fetch completes after the storage check`() =
            runTest {
                val storageDispatcher = QueuedDispatcher()
                val storage = TestStorage().apply { ignoreWrites = true }
                val client = client(this, storage, storageDispatcher)
                storageDispatcher.runAll() // Finish cache preload.

                client.updateConfigs()
                runCurrent() // The first refresh queues its storage check.
                storageDispatcher.runAll()
                runCurrent() // HTTP succeeds, but persistence is still queued.

                client.updateConfigs()
                runCurrent() // The second refresh queues a storage check too.
                storageDispatcher.runAll() // Persist and read before either continuation resumes.
                runCurrent() // Publish the first success before processing the stale storage result.

                verify(exactly = 1) { http.request(any()) }
            }
    }

    private fun fileStorage(): RemoteConfigStorage = FileRemoteConfigStorage(directory, "test-key", "test-instance", logger)

    private fun mode(waitForRemote: Boolean): DeliveryMode =
        if (waitForRemote) DeliveryMode.WaitForRemote(1_000L) else DeliveryMode.All

    private fun client(
        scope: TestScope,
        storage: RemoteConfigStorage,
        storageDispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler),
    ) = RemoteConfigClientImpl(
        apiKey = "test-key",
        serverZone = ServerZone.US,
        coroutineScope = scope,
        networkIODispatcher = StandardTestDispatcher(scope.testScheduler),
        storageIODispatcher = storageDispatcher,
        remoteConfigStorage = storage,
        httpClient = http,
        logger = logger,
        time = time,
    )

    private fun RemoteConfigClient.deliveries(
        mode: DeliveryMode = DeliveryMode.WaitForRemote(1_000L),
        key: Key = Key.Custom("sessionReplay"),
    ): List<Triple<ConfigMap?, Source, Long?>> {
        val deliveries = mutableListOf<Triple<ConfigMap?, Source, Long?>>()
        val callback = RemoteConfigClient.RemoteConfigCallback {
                config,
                source,
                timestamp,
            ->
            deliveries.add(Triple(config, source, timestamp))
        }
        callbacks.add(callback)
        subscribe(key, mode, callback)
        return deliveries
    }

    private class TestStorage(var cached: CachedRemoteConfig? = null) : RemoteConfigStorage {
        var failReads = false
        var failWrites = false
        var ignoreWrites = false

        override fun read(): CachedRemoteConfig? {
            check(!failReads) { "Cache unavailable" }
            return cached
        }

        override suspend fun write(value: CachedRemoteConfig) {
            check(!failWrites) { "Disk full" }
            if (!ignoreWrites) cached = value
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
