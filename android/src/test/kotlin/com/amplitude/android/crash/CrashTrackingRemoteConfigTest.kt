@file:OptIn(RestrictedAmplitudeFeature::class)

package com.amplitude.android.crash

import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.remoteconfig.ConfigMap
import com.amplitude.core.remoteconfig.RemoteConfigClient
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashTrackingRemoteConfigTest {
    @Test
    fun `stays disabled without remote config`() {
        val remoteConfig = CrashTrackingRemoteConfig(TestRemoteConfigClient(), sdkVersion = "1.8.0")
        remoteConfig.initialize()

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `stays disabled until initialize subscribes`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = CrashTrackingRemoteConfig(client, sdkVersion = "1.8.0")

        client.emit(mapOf("availabilities" to mapOf("CrashTracking" to "1.7.0")))
        assertFalse(remoteConfig.isCrashTrackingEnabled)

        remoteConfig.initialize()
        client.emit(mapOf("availabilities" to mapOf("CrashTracking" to "1.7.0")))
        assertTrue(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `enables crash tracking when SDK version meets availableFrom`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = CrashTrackingRemoteConfig(client, sdkVersion = "1.8.0")
        remoteConfig.initialize()

        client.emit(mapOf("availabilities" to mapOf("CrashTracking" to "1.7.0")))

        assertTrue(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `does not enable crash tracking below available version`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = CrashTrackingRemoteConfig(client, sdkVersion = "1.6.9")
        remoteConfig.initialize()

        client.emit(mapOf("availabilities" to mapOf("CrashTracking" to "1.7.0")))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `does not enable crash tracking without CrashTracking availability`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = CrashTrackingRemoteConfig(client, sdkVersion = "1.8.0")
        remoteConfig.initialize()

        client.emit(mapOf("enabled" to true, "sampleRate" to 1.0))

        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

    @Test
    fun `disables crash tracking when minimum version increases`() {
        val client = TestRemoteConfigClient()
        val remoteConfig = CrashTrackingRemoteConfig(client, sdkVersion = "1.8.0")
        remoteConfig.initialize()

        client.emit(mapOf("availabilities" to mapOf("CrashTracking" to "1.8.0")))
        assertTrue(remoteConfig.isCrashTrackingEnabled)

        client.emit(mapOf("availabilities" to mapOf("CrashTracking" to "1.9.0")))
        assertFalse(remoteConfig.isCrashTrackingEnabled)
    }

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
            config: ConfigMap,
            source: RemoteConfigClient.Source = RemoteConfigClient.Source.REMOTE,
            timestamp: Long = System.currentTimeMillis(),
        ) {
            callbacks.forEach { it.onUpdate(config, source, timestamp) }
        }
    }
}
