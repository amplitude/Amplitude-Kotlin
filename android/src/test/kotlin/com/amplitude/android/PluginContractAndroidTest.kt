package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.enterForeground
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Phase 0 — Android-specific Plugin contract wiring (session-id callback, reset
 * via the AndroidContextPlugin path, and dedup against the platform timeline).
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class PluginContractAndroidTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun configuration(): Configuration {
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
            instanceName = "plugin-contract-test",
            minTimeBetweenSessionsMillis = 100,
            storageProvider = InMemoryStorageProvider(),
            autocapture =
                autocaptureOptions {
                    +sessions
                },
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
        )
    }

    @Test
    fun `onSessionIdChanged fires when a new session starts`() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    server = null,
                    scheduler = testScheduler,
                    configuration = configuration(),
                )
            val recorder = SessionRecordingPlugin()
            amplitude.add(recorder)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            enterForeground(amplitude, 1_000L)

            assertTrue("expected onSessionIdChanged to fire, saw ${recorder.sessionIds}", recorder.sessionIds.isNotEmpty())
            assertEquals(amplitude.sessionId, recorder.sessionIds.last())
        }

    @Test
    fun `onSessionIdChanged also fires for ObservePlugins in the store`() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    server = null,
                    scheduler = testScheduler,
                    configuration = configuration(),
                )
            val observer = SessionRecordingObservePlugin()
            amplitude.add(observer)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            enterForeground(amplitude, 2_000L)

            assertTrue("ObservePlugin missed onSessionIdChanged: ${observer.sessionIds}", observer.sessionIds.isNotEmpty())
        }

    @Test
    fun `reset rotates deviceId and emits one bundled identity-change plus onReset`() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    server = null,
                    scheduler = testScheduler,
                    configuration = configuration(),
                )
            amplitude.isBuilt.await()
            amplitude.setUserId("u-1")
            advanceUntilIdle()
            val before = amplitude.getDeviceId()

            val recorder = ResetRecordingPlugin()
            amplitude.add(recorder)

            amplitude.reset()
            advanceUntilIdle()

            assertNotNull(before)
            assertNotEquals(before, amplitude.getDeviceId())
            assertEquals(1, recorder.userIds.size)
            assertEquals(1, recorder.deviceIds.size)
            assertEquals(1, recorder.resets)
        }

    @Test
    fun `add evicts a previously-registered plugin sharing the same name`() =
        runTest {
            val amplitude =
                createFakeAmplitude(
                    server = null,
                    scheduler = testScheduler,
                    configuration = configuration(),
                )
            amplitude.isBuilt.await()

            val first = NamedPlugin("dedup-target")
            val second = NamedPlugin("dedup-target")
            amplitude.add(first)
            amplitude.add(second)

            assertTrue(first.tornDown)
            assertSame(second, amplitude.findPluginByName("dedup-target"))
        }
}

private class SessionRecordingPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
    override val name: String = "session-recorder"
    val sessionIds = mutableListOf<Long>()

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun onSessionIdChanged(sessionId: Long) {
        sessionIds += sessionId
    }
}

private class SessionRecordingObservePlugin : ObservePlugin() {
    override lateinit var amplitude: com.amplitude.core.Amplitude
    override val name: String = "session-observer"
    val sessionIds = mutableListOf<Long>()

    override fun onUserIdChanged(userId: String?) {}

    override fun onDeviceIdChanged(deviceId: String?) {}

    override fun onSessionIdChanged(sessionId: Long) {
        sessionIds += sessionId
    }
}

private class ResetRecordingPlugin : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
    override val name: String = "reset-recorder"
    val userIds = mutableListOf<String?>()
    val deviceIds = mutableListOf<String?>()
    var resets: Int = 0

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

private class NamedPlugin(override val name: String?) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
    var tornDown: Boolean = false

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        tornDown = true
    }
}
