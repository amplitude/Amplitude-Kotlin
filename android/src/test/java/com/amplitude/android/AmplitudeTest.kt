package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.Identity
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.common.android.AndroidContextProvider
import com.amplitude.core.StorageProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPlugin
import com.amplitude.core.platform.Plugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

open class StubPlugin : EventPlugin {
    override val type: Plugin.Type = Plugin.Type.Before
    override lateinit var amplitude: com.amplitude.core.Amplitude
}

@ExperimentalCoroutinesApi
class AmplitudeTest {
    private var context: Context? = null
    private var amplitude: Amplitude? = null
    private lateinit var connectivityManager: ConnectivityManager

    @BeforeEach
    fun setUp() {
        context = mockk<Application>(relaxed = true)
        connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context!!.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager

        mockkStatic(AndroidLifecyclePlugin::class)

        mockkConstructor(AndroidContextProvider::class)
        every { anyConstructed<AndroidContextProvider>().osName } returns "android"
        every { anyConstructed<AndroidContextProvider>().osVersion } returns "10"
        every { anyConstructed<AndroidContextProvider>().brand } returns "google"
        every { anyConstructed<AndroidContextProvider>().manufacturer } returns "Android"
        every { anyConstructed<AndroidContextProvider>().model } returns "Android SDK built for x86"
        every { anyConstructed<AndroidContextProvider>().language } returns "English"
        every { anyConstructed<AndroidContextProvider>().advertisingId } returns ""
        every { anyConstructed<AndroidContextProvider>().versionName } returns "1.0"
        every { anyConstructed<AndroidContextProvider>().carrier } returns "Android"
        every { anyConstructed<AndroidContextProvider>().country } returns "US"
        every { anyConstructed<AndroidContextProvider>().mostRecentLocation } returns null
        every { anyConstructed<AndroidContextProvider>().appSetId } returns ""

        amplitude = Amplitude(createConfiguration())
    }

    private fun setDispatcher(testScheduler: TestCoroutineScheduler) {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // inject the amplitudeDispatcher field with reflection, as the field is val (read-only)
        val amplitudeDispatcherField = com.amplitude.core.Amplitude::class.java.getDeclaredField("amplitudeDispatcher")
        amplitudeDispatcherField.isAccessible = true
        amplitudeDispatcherField.set(amplitude, dispatcher)
    }

    private fun createConfiguration(minTimeBetweenSessionsMillis: Long? = null, storageProvider: StorageProvider = InMemoryStorageProvider()): Configuration {
        val configuration = Configuration(
            apiKey = "api-key",
            context = context!!,
            instanceName = instanceName,
            storageProvider = storageProvider,
            trackingSessionEvents = minTimeBetweenSessionsMillis != null,
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
        )

        if (minTimeBetweenSessionsMillis != null) {
            configuration.minTimeBetweenSessionsMillis = minTimeBetweenSessionsMillis
        }

        return configuration
    }

    @Test
    fun amplitude_reset_wipesUserIdDeviceId() = runTest {
        setDispatcher(testScheduler)
        if (amplitude?.isBuilt!!.await()) {
            amplitude?.setUserId("test user")
            amplitude?.setDeviceId("test device")
            advanceUntilIdle()
            Assertions.assertEquals("test user", amplitude?.store?.userId)
            Assertions.assertEquals("test device", amplitude?.store?.deviceId)
            Assertions.assertEquals("test user", amplitude?.getUserId())
            Assertions.assertEquals("test device", amplitude?.getDeviceId())

            amplitude?.reset()
            advanceUntilIdle()
            Assertions.assertNull(amplitude?.store?.userId)
            Assertions.assertNotEquals("test device", amplitude?.store?.deviceId)
            Assertions.assertNull(amplitude?.getUserId())
            Assertions.assertNotEquals("test device", amplitude?.getDeviceId())
        }
    }

    @Test
    fun amplitude_unset_country_with_remote_ip() = runTest {
        setDispatcher(testScheduler)
        val mockedPlugin = spyk(StubPlugin())
        amplitude?.add(mockedPlugin)

        if (amplitude?.isBuilt!!.await()) {
            val event = BaseEvent()
            event.eventType = "test event"
            amplitude?.track(event)
            advanceUntilIdle()
            Thread.sleep(100)

            val track = slot<BaseEvent>()
            verify { mockedPlugin.track(capture(track)) }
            track.captured.let {
                Assertions.assertEquals("\$remote", it.ip)
                Assertions.assertNull(it.country)
            }
        }
    }

    @Test
    fun amplitude_fetch_country_with_customized_ip() = runTest {
        setDispatcher(testScheduler)
        val mockedPlugin = spyk(StubPlugin())
        amplitude?.add(mockedPlugin)

        if (amplitude?.isBuilt!!.await()) {
            val event = BaseEvent()
            event.eventType = "test event"
            event.ip = "127.0.0.1"
            amplitude?.track(event)
            advanceUntilIdle()
            Thread.sleep(100)

            val track = slot<BaseEvent>()
            verify { mockedPlugin.track(capture(track)) }
            track.captured.let {
                Assertions.assertEquals("127.0.0.1", it.ip)
                Assertions.assertEquals("US", it.country)
            }
        }
    }

    @Test
    fun test_analytics_connector() = runTest {
        setDispatcher(testScheduler)
        val mockedPlugin = spyk(StubPlugin())
        amplitude?.add(mockedPlugin)

        if (amplitude?.isBuilt!!.await()) {

            val connector = AnalyticsConnector.getInstance(instanceName)
            val connectorUserId = "connector user id"
            val connectorDeviceId = "connector device id"
            var connectorIdentitySet = false
            val identityListener = { _: Identity ->
                if (connectorIdentitySet) {
                    Assertions.assertEquals(connectorUserId, connector.identityStore.getIdentity().userId)
                    Assertions.assertEquals(connectorDeviceId, connector.identityStore.getIdentity().deviceId)
                    connectorIdentitySet = false
                }
            }
            connector.identityStore.addIdentityListener(identityListener)
            amplitude?.setUserId(connectorUserId)
            amplitude?.setDeviceId(connectorDeviceId)
            advanceUntilIdle()
            connectorIdentitySet = true
            connector.identityStore.removeIdentityListener(identityListener)
        }
    }

    companion object {
        const val instanceName = "testInstance"
    }
}
