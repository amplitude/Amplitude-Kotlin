package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.IdentityListener
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.android.utilities.enterForeground
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.Amplitude
import com.amplitude.core.AmplitudeContext
import com.amplitude.core.AnalyticsClient
import com.amplitude.core.AnalyticsIdentity
import com.amplitude.core.Storage
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.events.IdentifyEvent
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.ObservePlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorage
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IMIdentityStorage
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityStorage
import com.amplitude.id.IdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `prebuild observers are not notified of restored Android identity`() =
        runTest {
            val storage = IMIdentityStorage().apply {
                userId = "restored-user"
                deviceId = "restored-device"
            }
            val amplitude = createTestAmplitude(
                instanceName = "identity-ready-restored",
                identityStorageProvider = object : IdentityStorageProvider {
                    override fun getIdentityStorage(configuration: IdentityConfiguration): IdentityStorage = storage
                },
            )
            val identities = mutableListOf<AnalyticsIdentity>()
            amplitude.add(
                object : UniversalPlugin {
                    override fun setup(client: AnalyticsClient, context: AmplitudeContext) {
                        assertNull(client.identity.userId)
                        assertNull(client.identity.deviceId)
                    }

                    override fun onIdentityChanged(identity: AnalyticsIdentity) {
                        identities += identity
                    }
                },
            )

            amplitude.isBuilt.await()
            advanceUntilIdle()

            assertTrue(identities.isEmpty())
            assertEquals("restored-user", amplitude.identity.userId)
            assertEquals("restored-device", amplitude.identity.deviceId)
        }

    @Test
    fun `first launch generated device id notifies prebuild observers once`() =
        runTest {
            val amplitude = createTestAmplitude(instanceName = "identity-ready-generated")
            val identities = mutableListOf<AnalyticsIdentity>()
            amplitude.add(
                object : UniversalPlugin {
                    override fun onIdentityChanged(identity: AnalyticsIdentity) {
                        identities += identity
                    }
                },
            )

            amplitude.isBuilt.await()
            advanceUntilIdle()

            assertEquals(1, identities.size)
            assertNotNull(identities.single().deviceId)
            assertEquals(amplitude.getDeviceId(), identities.single().deviceId)
        }

    @Test
    fun `session callback fires for Plugin, UniversalPlugin, and ObservePlugin`() =
        runTest {
            val amplitude = createTestAmplitude()
            val timeline = SessionPlugin("timeline")
            val universal = SessionUniversalPlugin()
            val observe = SessionObservePlugin("observe")

            amplitude.add(timeline)
            amplitude.add(universal)
            amplitude.add(observe)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            enterForeground(amplitude, 1_000L)

            assertEquals(listOf(amplitude.sessionId), timeline.sessionIds)
            assertEquals(listOf(amplitude.sessionId), universal.sessionIds)
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
    fun `persisted session restore reaches an ObservePlugin`() =
        runTest {
            val storage = InMemoryStorage()
            storage.write(Storage.Constants.PREVIOUS_SESSION_ID, "12345")
            val amplitude = createTestAmplitude(storageProvider = SharedStorageProvider(storage))
            val observe = SessionObservePlugin("observe-restore")

            amplitude.add(observe)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            assertEquals(listOf(12345L), observe.sessionIds)
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
            assertEquals(1, recorder.identities)
        }

    @Test
    fun `unchanged ids including identify options notify neither plugins nor connector`() =
        runTest {
            val amplitude = createTestAmplitude(instanceName = "unchanged-id-callbacks")
            amplitude.isBuilt.await()
            advanceUntilIdle()
            amplitude.setUserId("user")
            amplitude.setDeviceId("device")
            val recorder = ResetPlugin("recorder")
            amplitude.add(recorder)
            val connectorUpdates = mutableListOf<String?>()
            val connector = AnalyticsConnector.getInstance(amplitude.configuration.instanceName)
            val listener: IdentityListener = { connectorUpdates += it.userId }
            connector.identityStore.addIdentityListener(listener)

            amplitude.setUserId("user")
            amplitude.setDeviceId("device")
            amplitude.identify(Identify(), EventOptions().apply { userId = "user"; deviceId = "device" })
            advanceUntilIdle()

            assertEquals(emptyList<String?>(), recorder.userIds)
            assertEquals(emptyList<String?>(), recorder.deviceIds)
            assertEquals(0, recorder.identities)
            assertEquals(emptyList<String?>(), connectorUpdates)
            connector.identityStore.removeIdentityListener(listener)
        }

    @Test
    fun `anonymous Android reset still rotates connector device id and sends one identity`() =
        runTest {
            val amplitude = createTestAmplitude(instanceName = "anonymous-reset-callbacks")
            amplitude.isBuilt.await()
            advanceUntilIdle()
            val recorder = ResetPlugin("recorder")
            amplitude.add(recorder)
            val connector = AnalyticsConnector.getInstance(amplitude.configuration.instanceName)
            val before = connector.identityStore.getIdentity().deviceId

            amplitude.reset()
            advanceUntilIdle()

            assertEquals(emptyList<String?>(), recorder.userIds)
            assertEquals(listOf(amplitude.getDeviceId()), recorder.deviceIds)
            assertEquals(1, recorder.identities)
            assertEquals(1, recorder.resets)
            assertNotEquals(before, connector.identityStore.getIdentity().deviceId)
            assertEquals(amplitude.getDeviceId(), connector.identityStore.getIdentity().deviceId)
            assertNull(connector.identityStore.getIdentity().userId)
        }

    @Test
    fun `named plugin add keeps the first Android plugin`() =
        runTest {
            val amplitude = createTestAmplitude()
            amplitude.isBuilt.await()
            advanceUntilIdle()
            val first = NamedPlugin("shared")
            val second = NamedPlugin("shared")

            amplitude.add(first)
            amplitude.add(second)

            // First registration wins; incumbent is not torn down.
            assertSame(first, amplitude.findPlugin<NamedPlugin>())
            assertFalse(first.tornDown)
            // Newcomer was never set up.
            assertFalse(second.wasSetUp)
        }

    private fun TestScope.createTestAmplitude(
        storageProvider: StorageProvider = InMemoryStorageProvider(),
        identityStorageProvider: IdentityStorageProvider = IMIdentityStorageProvider(),
        instanceName: String = "plugin-contract-android-test",
    ): com.amplitude.android.Amplitude {
        return createFakeAmplitude(
            server = null,
            scheduler = testScheduler,
            configuration = configuration(storageProvider, identityStorageProvider, instanceName),
        )
    }

    private fun configuration(
        storageProvider: StorageProvider,
        identityStorageProvider: IdentityStorageProvider,
        instanceName: String,
    ): Configuration {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        every { context.applicationContext } returns context
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }

        return Configuration(
            apiKey = "api-key",
            context = context,
            instanceName = instanceName,
            minTimeBetweenSessionsMillis = 100,
            storageProvider = storageProvider,
            autocapture = autocaptureOptions { +sessions },
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = identityStorageProvider,
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

private class SessionUniversalPlugin : UniversalPlugin {
    val sessionIds = mutableListOf<Long>()

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
    var identities = 0

    override fun onIdentityChanged(identity: AnalyticsIdentity) {
        identities += 1
    }

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
    var wasSetUp = false

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        wasSetUp = true
    }

    override fun execute(event: BaseEvent): BaseEvent = event

    override fun teardown() {
        tornDown = true
    }
}
