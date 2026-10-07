@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.android.crash

import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CrashTrackingRemoteConfigTest {
    @Test
    fun `stays disabled without remote config when the previous run was off`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", previousRunEnabled = false)

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `uses the previous run until remote config arrives`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", previousRunEnabled = true)

        assertTrue(remoteConfig.isCrashTrackingEnabled)

        client.emit(crashTrackingConfig(availableFrom = "1.9.0"))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `enables crash tracking when SDK version meets availableFrom`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0")

        client.emit(crashTrackingConfig(availableFrom = "1.7.0"))

        assertTrue(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `does not enable crash tracking below available version`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.6.9")

        client.emit(crashTrackingConfig(availableFrom = "1.7.0"))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `does not enable crash tracking without CrashTracking availability`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0")

        client.emit(mapOf("enabled" to true, "sampleRate" to 1.0))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `does not enable crash tracking when diagnostics is disabled remotely`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0")

        client.emit(crashTrackingConfig(availableFrom = "1.7.0", enabled = false))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `does not enable crash tracking when the sample rate is zero`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0")

        client.emit(crashTrackingConfig(availableFrom = "1.7.0", sampleRate = 0.0))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `disables crash tracking when minimum version increases`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0")

        client.emit(crashTrackingConfig(availableFrom = "1.8.0"))
        assertTrue(remoteConfig.isCrashTrackingEnabled)

        client.emit(crashTrackingConfig(availableFrom = "1.9.0"))
        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `persists enabled when every gate passes`() {
        val client = TestRemoteConfigClient()
        val store = store()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", store = store)

        client.emit(crashTrackingConfig(availableFrom = "1.7.0"))

        assertTrue(remoteConfig.isCrashTrackingEnabled)
        verify(exactly = 1) { store.setEnabled(true) }
    }

    @Test
    fun `persists disabled when availableFrom is raised above the SDK version`() {
        val client = TestRemoteConfigClient()
        val store = store()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", store = store)

        client.emit(crashTrackingConfig(availableFrom = "1.8.0"))
        client.emit(crashTrackingConfig(availableFrom = "1.9.0"))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
        verify(exactly = 1) { store.setEnabled(true) }
        verify(exactly = 1) { store.setEnabled(false) }
    }

    @Test
    fun `persists disabled when a successful fetch drops CrashTracking`() {
        val client = TestRemoteConfigClient()
        val store = store()
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", store = store)

        client.emit(crashTrackingConfig(availableFrom = "1.7.0"))
        client.emit(mapOf("enabled" to true, "sampleRate" to 1.0))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
        verify(exactly = 1) { store.setEnabled(false) }
    }

    @Test
    fun `persists disabled when a successful fetch has no diagnostics config`() {
        val client = TestRemoteConfigClient()
        val store = store(previousRunEnabled = true)
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", store = store)

        client.emit(config = null)

        assertFalse(remoteConfig.isCrashTrackingEnabled)
        verify(exactly = 1) { store.setEnabled(false) }
    }

    @Test
    fun `keeps the previous run when a fetch fails`() {
        val client = TestRemoteConfigClient()
        val store = store(previousRunEnabled = true)
        val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", store = store)

        client.emit(config = null, timestamp = null)

        assertTrue(remoteConfig.isCrashTrackingEnabled)
        verify(exactly = 0) { store.setEnabled(any()) }
    }

    @Nested
    inner class Detached {
        @Test
        fun `ignores a late callback that disables crash tracking`() {
            val client = TestRemoteConfigClient()
            val store = store()
            val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", store = store)
            client.emit(crashTrackingConfig(availableFrom = "1.7.0"))

            remoteConfig.detach()
            client.emit(mapOf("enabled" to true, "sampleRate" to 1.0))
            client.emit(config = null)

            assertTrue(remoteConfig.isCrashTrackingEnabled)
            verify(exactly = 1) { store.setEnabled(true) }
            verify(exactly = 0) { store.setEnabled(false) }
        }

        @Test
        fun `ignores the first callback when detached before config arrives`() {
            val client = TestRemoteConfigClient()
            val store = store()
            val remoteConfig = remoteConfig(client, sdkVersion = "1.8.0", store = store)

            remoteConfig.detach()
            remoteConfig.detach()
            client.emit(crashTrackingConfig(availableFrom = "1.7.0"))

            assertFalse(remoteConfig.isCrashTrackingEnabled)
            verify(exactly = 0) { store.setEnabled(any()) }
        }
    }

    private fun crashTrackingConfig(
        availableFrom: String,
        enabled: Boolean = true,
        sampleRate: Double = 1.0,
    ): ConfigMap =
        mapOf(
            "enabled" to enabled,
            "sampleRate" to sampleRate,
            "availabilities" to mapOf("CrashTracking" to availableFrom),
        )

    private fun store(previousRunEnabled: Boolean = false): CrashTrackingEnabledStore =
        mockk<CrashTrackingEnabledStore>(relaxed = true).also {
            every { it.isEnabled() } returns previousRunEnabled
        }

    private fun remoteConfig(
        client: TestRemoteConfigClient,
        sdkVersion: String,
        previousRunEnabled: Boolean = false,
        store: CrashTrackingEnabledStore = store(previousRunEnabled),
    ) = CrashTrackingRemoteConfig(
        remoteConfigClient = client,
        sdkVersion = sdkVersion,
        crashTrackingEnabledStore = store,
    )

    private class TestRemoteConfigClient : RemoteConfigClient {
        private val callbacks = mutableListOf<RemoteConfigClient.RemoteConfigCallback>()

        override fun subscribe(
            key: RemoteConfigClient.Key,
            deliveryMode: RemoteConfigClient.DeliveryMode,
            callback: RemoteConfigClient.RemoteConfigCallback,
        ) {
            if (key == RemoteConfigClient.Key.Diagnostics) {
                callbacks.add(callback)
            }
        }

        override fun updateConfigs() {}

        fun emit(
            config: ConfigMap?,
            source: RemoteConfigClient.Source = RemoteConfigClient.Source.REMOTE,
            timestamp: Long? = System.currentTimeMillis(),
        ) {
            callbacks.forEach { it.onUpdate(config, source, timestamp) }
        }
    }
}
