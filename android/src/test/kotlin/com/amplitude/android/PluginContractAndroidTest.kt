package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.enterForeground
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.Amplitude
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class PluginContractAndroidTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `session callback fires for new and observe plugins`() =
        runTest {
            val amplitude = createTestAmplitude()
            val timeline = SessionPlugin("timeline")
            val observe = SessionObservePlugin("observe")

            amplitude.add(timeline)
            amplitude.add(observe)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            enterForeground(amplitude, 1_000L)

            assertEquals(listOf(amplitude.sessionId), timeline.sessionIds)
            assertEquals(listOf(amplitude.sessionId), observe.sessionIds)
        }

    @Test
    fun `session callback fires when persisted session is restored`() =
        runTest {
            val storage = InMemoryStorage()
            storage.write(Storage.Constants.PREVIOUS_SESSION_ID, "12345")
            val amplitude = createTestAmplitude(storageProvider = SharedStorageProvider(storage))
            val timeline = SessionPlugin("timeline")

            amplitude.add(timeline)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            assertEquals(listOf(12345L), timeline.sessionIds)
        }

    @Test
    fun `reset rotates deviceId and uses one identity reset notification`() =
        runTest {
            val amplitude = createTestAmplitude()
            amplitude.isBuilt.await()
            advanceUntilIdle()
            amplitude.setUserId("user-1")
            val before = amplitude.getDeviceId()
            val recorder = ResetPlugin("reset")

            amplitude.add(recorder)
            amplitude.reset()
            advanceUntilIdle()

            assertNotNull(before)
            assertNotEquals(before, amplitude.getDeviceId())
            assertEquals(listOf<String?>(null), recorder.userIds)
            assertEquals(1, recorder.deviceIds.size)
            assertEquals(1, recorder.resets)
        }

    @Test
    fun `named plugin add replaces old Android plugin`() =
        runTest {
            val amplitude = createTestAmplitude()
            amplitude.isBuilt.await()
            advanceUntilIdle()
            val first = NamedPlugin("shared")
            val second = NamedPlugin("shared")

            amplitude.add(first)
            amplitude.add(second)

            assertTrue(first.tornDown)
            assertSame(second, amplitude.findPlugin<NamedPlugin>())
        }

    private fun TestScope.createTestAmplitude(
        storageProvider: StorageProvider = InMemoryStorageProvider(),
    ): com.amplitude.android.Amplitude {
        return createFakeAmplitude(
            server = null,
            scheduler = testScheduler,
            configuration = configuration(storageProvider),
        )
    }

    private fun configuration(storageProvider: StorageProvider): Configuration {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }

        return Configuration(
            apiKey = "api-key",
            context = context,
            instanceName = "plugin-contract-android-test",
            minTimeBetweenSessionsMillis = 100,
            storageProvider = storageProvider,
            autocapture = autocaptureOptions { +sessions },
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
            enableAutocaptureRemoteConfig = false,
            enableDiagnostics = false,
        )
    }
}

private class SharedStorageProvider(
    private val storage: Storage,
) : StorageProvider {
    override fun getStorage(
        amplitude: Amplitude,
        prefix: String?,
    ): Storage = storage
}

private class SessionPlugin(override val name: String) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    val sessionIds = mutableListOf<Long>()

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun onSessionIdChanged(sessionId: Long) {
        sessionIds += sessionId
    }
}

private class SessionObservePlugin(override val name: String) : ObservePlugin() {
    override lateinit var amplitude: Amplitude
    val sessionIds = mutableListOf<Long>()

    override fun onUserIdChanged(userId: String?) {}

    override fun onDeviceIdChanged(deviceId: String?) {}

    override fun onSessionIdChanged(sessionId: Long) {
        sessionIds += sessionId
    }
}

private class ResetPlugin(override val name: String) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    val userIds = mutableListOf<String?>()
    val deviceIds = mutableListOf<String?>()
    var resets = 0

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun onUserIdChanged(userId: String?) {
        userIds += userId
    }

    override fun onDeviceIdChanged(deviceId: String?) {
        deviceIds += deviceId
    }

    override fun onReset() {
        resets += 1
    }
}

private class NamedPlugin(override val name: String) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: Amplitude
    var tornDown = false

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        tornDown = true
    }
}
