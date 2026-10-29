package com.amplitude.core

import com.amplitude.common.jvm.ConsoleLogger
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.remoteconfig.CachedRemoteConfig
import com.amplitude.core.remoteconfig.FileRemoteConfigStorage
import com.amplitude.core.remoteconfig.RemoteConfigClient
import com.amplitude.core.remoteconfig.RemoteConfigStorage
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(RestrictedAmplitudeFeature::class)
class AmplitudeContextTest {
    @Test
    fun `restricted clients initialize lazily once`() {
        val remoteConfigClient = mockk<RemoteConfigClient>()
        val diagnosticsClient = mockk<DiagnosticsClient>()
        var remoteConfigInitializations = 0
        var diagnosticsInitializations = 0
        val context =
            AmplitudeContext(
                apiKey = "api-key",
                instanceName = "instance",
                serverZone = ServerZone.US,
                logger = ConsoleLogger.logger,
                remoteConfigClientProvider = {
                    remoteConfigInitializations += 1
                    remoteConfigClient
                },
                diagnosticsClientProvider = {
                    diagnosticsInitializations += 1
                    diagnosticsClient
                },
            )

        assertEquals(0, remoteConfigInitializations)
        assertEquals(0, diagnosticsInitializations)

        assertSame(remoteConfigClient, context.remoteConfigClient)
        assertSame(remoteConfigClient, context.remoteConfigClient)
        assertSame(diagnosticsClient, context.diagnosticsClient)
        assertSame(diagnosticsClient, context.diagnosticsClient)

        assertEquals(1, remoteConfigInitializations)
        assertEquals(1, diagnosticsInitializations)
    }

    @Test
    fun `storage directory constructor creates its own clients`(
        @TempDir tempDir: File,
    ) {
        val context =
            AmplitudeContext(
                apiKey = "api-key",
                instanceName = "standalone",
                serverZone = ServerZone.US,
                logger = ConsoleLogger.logger,
                storageDirectory = tempDir,
            )

        assertNotNull(context.remoteConfigClient)
        assertNotNull(context.diagnosticsClient)
        context.close()
    }

    @Test
    fun `host context close is a no-op`() {
        val remoteConfigClient = mockk<RemoteConfigClient>()
        val diagnosticsClient = mockk<DiagnosticsClient>()
        val context =
            AmplitudeContext(
                apiKey = "api-key",
                instanceName = "instance",
                serverZone = ServerZone.US,
                logger = ConsoleLogger.logger,
                remoteConfigClient = remoteConfigClient,
                diagnosticsClient = diagnosticsClient,
            )

        context.close()

        verify(exactly = 0) { diagnosticsClient.close() }
    }

    @Test
    fun `new standalone context with the same identity delivers the persisted config as cache`(
        @TempDir tempDir: File,
    ) {
        val storage =
            FileRemoteConfigStorage(
                tempDir,
                "api-key",
                standaloneStorageNamespace("api-key", ServerZone.US, "standalone"),
                ConsoleLogger.logger,
            )
        runBlocking { storage.write(CachedRemoteConfig(CONFIG_JSON, 1234567890L)) }
        val context = standaloneContext(tempDir)
        val latch = CountDownLatch(1)
        var source: RemoteConfigClient.Source? = null
        val callback =
            RemoteConfigClient.RemoteConfigCallback { _, deliveredSource, _ ->
                if (source == null) source = deliveredSource
                latch.countDown()
            }

        context.remoteConfigClient.subscribe(RemoteConfigClient.Key.Custom("sessionReplay.a"), callback = callback)

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertEquals(RemoteConfigClient.Source.CACHE, source)
        context.close()
    }

    @Test
    fun `close cancels the shared scope`(
        @TempDir tempDir: File,
    ) {
        val context = standaloneContext(tempDir)
        val scope = scopeOf(context.remoteConfigClient)
        context.diagnosticsClient

        assertTrue(scope.isActive)
        context.close()

        assertFalse(scope.isActive)
    }

    @Test
    fun `closing diagnostics does not cancel the shared scope`(
        @TempDir tempDir: File,
    ) {
        val context = standaloneContext(tempDir)
        val scope = scopeOf(context.remoteConfigClient)

        context.diagnosticsClient.close()

        assertTrue(scope.isActive)
        context.close()
    }

    @Test
    fun `standalone contexts for other projects or regions do not share the remote config cache`(
        @TempDir tempDir: File,
    ) {
        val projectA = standaloneContext(tempDir)
        val projectB = standaloneContext(tempDir, apiKey = "other-api-key")
        val projectAEu = standaloneContext(tempDir, serverZone = ServerZone.EU)
        val cached = CachedRemoteConfig(CONFIG_JSON, 1234567890L)

        runBlocking { cacheOf(projectA.remoteConfigClient).write(cached) }
        runBlocking { cacheOf(projectB.remoteConfigClient).write(cached.copy(timestamp = 42L)) }

        assertEquals(cached.timestamp, cacheOf(projectA.remoteConfigClient).read()?.timestamp)
        assertEquals(42L, cacheOf(projectB.remoteConfigClient).read()?.timestamp)
        assertNull(cacheOf(projectAEu.remoteConfigClient).read())
        listOf(projectA, projectB, projectAEu).forEach { it.close() }
    }

    @Test
    fun `standalone contexts for other projects or regions do not share diagnostics storage`(
        @TempDir tempDir: File,
    ) {
        val contexts =
            listOf(
                standaloneContext(tempDir),
                standaloneContext(tempDir, apiKey = "other-api-key"),
                standaloneContext(tempDir, serverZone = ServerZone.EU),
            )

        val directories = contexts.map { diagnosticsDirectoryOf(it.diagnosticsClient) }

        assertEquals(3, directories.toSet().size)
        contexts.forEach { it.close() }
    }

    private fun standaloneContext(
        directory: File,
        apiKey: String = "api-key",
        serverZone: ServerZone = ServerZone.US,
    ) = AmplitudeContext(
        apiKey = apiKey,
        instanceName = "standalone",
        serverZone = serverZone,
        logger = ConsoleLogger.logger,
        storageDirectory = directory,
    )

    private fun scopeOf(client: RemoteConfigClient): CoroutineScope {
        val field = client.javaClass.getDeclaredField("coroutineScope")
        field.isAccessible = true
        return field.get(client) as CoroutineScope
    }

    private fun cacheOf(client: RemoteConfigClient): RemoteConfigStorage = privateField(client, "remoteConfigStorage")

    private fun diagnosticsDirectoryOf(client: DiagnosticsClient): String {
        val storage: Any = privateField(client, "storage")
        return privateField(storage, "sanitizedInstance")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> privateField(
        target: Any,
        name: String,
    ): T {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target) as T
    }

    private companion object {
        const val CONFIG_JSON = "{\"sessionReplay\":{\"a\":{\"b\":1}}}"
    }
}
