package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.DestinationPlugin
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CoppaDeviceIdTest {
    @Before
    fun setUp() {
        mockkStatic(AdvertisingIdClient::class)
        every { AdvertisingIdClient.getAdvertisingIdInfo(any()) } returns AdvertisingIdClient.Info(ADVERTISING_ID, false)
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `coppa control does not use advertising id as device id`() {
        val amplitude = amplitude(enableCoppaControl = true)

        assertNotEquals(ADVERTISING_ID, amplitude.getDeviceId())
    }

    @Test
    fun `coppa control does not use advertising id as device id after reset`() {
        val amplitude = amplitude(enableCoppaControl = true)

        amplitude.reset()

        assertNotEquals(ADVERTISING_ID, amplitude.getDeviceId())
    }

    @Test
    fun `advertising id is used as device id without coppa control`() {
        val amplitude = amplitude(enableCoppaControl = false)

        assertEquals(ADVERTISING_ID, amplitude.getDeviceId())
    }

    @Test
    fun `coppa events do not contain advertising id as device id`() {
        val amplitude = amplitude(enableCoppaControl = true)
        val events = LinkedBlockingQueue<BaseEvent>()
        amplitude.add(
            object : DestinationPlugin() {
                override fun track(payload: BaseEvent): BaseEvent {
                    events.add(payload)
                    return payload
                }
            },
        )

        amplitude.track("coppa event")

        val event = events.poll(5, TimeUnit.SECONDS)
        assertNotNull(event)
        assertNotNull(event!!.deviceId)
        assertNotEquals(ADVERTISING_ID, event.deviceId)
        assertNull(event.adid)
        assertNull(event.ip)
    }

    @Test
    fun `disabled adid is not used as device id on initialization or reset`() {
        val amplitude = amplitude(enableCoppaControl = false) { trackingOptions.disableAdid() }

        assertNotNull(amplitude.getDeviceId())
        assertNotEquals(ADVERTISING_ID, amplitude.getDeviceId())

        amplitude.reset()

        assertNotEquals(ADVERTISING_ID, amplitude.getDeviceId())
    }

    @Test
    fun `coppa preserves stored device id during initialization`() {
        val amplitude = amplitude(enableCoppaControl = true)
        amplitude.setDeviceId(ADVERTISING_ID)

        AndroidContextPlugin().setup(amplitude)

        assertEquals(ADVERTISING_ID, amplitude.getDeviceId())
    }

    @Test
    fun `coppa preserves configured device id on initialization and reset`() {
        val amplitude = amplitude(enableCoppaControl = true) { deviceId = "configured-device-id" }

        assertEquals("configured-device-id", amplitude.getDeviceId())

        amplitude.reset()

        assertEquals("configured-device-id", amplitude.getDeviceId())
    }

    private fun amplitude(
        enableCoppaControl: Boolean,
        configure: Configuration.() -> Unit = {},
    ): Amplitude {
        val amplitude =
            Amplitude(
                Configuration(
                    apiKey = "test-api-key",
                    context = ApplicationProvider.getApplicationContext<Context>(),
                    instanceName = "coppa-device-id-" + UUID.randomUUID(),
                    autocapture = emptySet(),
                    offline = true,
                    enableDiagnostics = false,
                    enableAutocaptureRemoteConfig = false,
                    identityStorageProvider = IMIdentityStorageProvider(),
                    storageProvider = InMemoryStorageProvider(),
                    // App Set ID disabled: the real GMS AppSet Tasks.await hangs under Robolectric.
                    trackingOptions = TrackingOptions().disableAppSetId(),
                    enableCoppaControl = enableCoppaControl,
                    useAdvertisingIdForDeviceId = true,
                ).apply(configure),
            )
        runBlocking { amplitude.isBuilt.await() }
        return amplitude
    }

    private companion object {
        const val ADVERTISING_ID = "ad-id-0000-1111-2222"
    }
}
