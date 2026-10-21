package com.amplitude.core

import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.utils.FakeAmplitude
import com.amplitude.id.IMIdentityStorage
import com.amplitude.id.IdentityConfiguration
import com.amplitude.id.IdentityStorage
import com.amplitude.id.IdentityStorageProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
internal class IdentityReadinessTest {
    @Test
    fun `prebuild plugins are not notified of restored identity`() =
        runTest {
            val amplitude = FakeAmplitude(configuration(), testDispatcher = StandardTestDispatcher(testScheduler))
            val observer = Recorder()
            amplitude.add(observer)
            assertNull(observer.initialIdentity?.userId)
            assertNull(observer.initialIdentity?.deviceId)

            advanceUntilIdle()

            assertTrue(amplitude.isBuilt.await())
            assertTrue(observer.identities.isEmpty())
            assertEquals("restored-user", amplitude.identity.userId)
            assertEquals("restored-device", amplitude.identity.deviceId)
        }

    @Test
    fun `configured device id notifies prebuild plugins once`() =
        runTest {
            val amplitude = FakeAmplitude(configuration("configured-device"), testDispatcher = StandardTestDispatcher(testScheduler))
            val observer = Recorder()
            amplitude.add(observer)

            advanceUntilIdle()

            assertEquals(listOf("configured-device"), observer.identities.map { it.deviceId })
            assertEquals(listOf("restored-user"), observer.identities.map { it.userId })
        }

    @Test
    fun `explicit prebuild setter is not repeated after bootstrap`() =
        runTest {
            val amplitude = FakeAmplitude(configuration(), testDispatcher = StandardTestDispatcher(testScheduler))
            amplitude.optOut = true
            val observer = Recorder()
            amplitude.add(observer)
            amplitude.setIdentity("explicit-user", "explicit-device", mapOf("plan" to "paid"))

            advanceUntilIdle()

            assertEquals(listOf("explicit-user"), observer.identities.map { it.userId })
            assertEquals(mapOf("plan" to "paid"), observer.identities.single().userProperties)
        }

    @Test
    fun `an observer added after build reads identity in setup without replay`() =
        runTest {
            val amplitude = FakeAmplitude(configuration(), testDispatcher = StandardTestDispatcher(testScheduler))
            advanceUntilIdle()
            val observer = Recorder()

            amplitude.add(observer)

            assertEquals("restored-user", observer.initialIdentity?.userId)
            assertEquals("restored-device", observer.initialIdentity?.deviceId)
            assertTrue(observer.identities.isEmpty())
        }

    private fun configuration(deviceId: String? = null): Configuration {
        val storage =
            IMIdentityStorage().apply {
                userId = "restored-user"
                this.deviceId = "restored-device"
            }
        return Configuration(
            apiKey = "test",
            instanceName = "identity-readiness-${UUID.randomUUID()}",
            deviceId = deviceId,
            identityStorageProvider =
                object : IdentityStorageProvider {
                    override fun getIdentityStorage(configuration: IdentityConfiguration): IdentityStorage = storage
                },
        )
    }

    private class Recorder : UniversalPlugin {
        var initialIdentity: AnalyticsIdentity? = null
        val identities = mutableListOf<AnalyticsIdentity>()

        override fun setup(
            client: AnalyticsClient,
            context: AmplitudeContext,
        ) {
            initialIdentity = client.identity
        }

        override fun onIdentityChanged(identity: AnalyticsIdentity) {
            identities += identity
        }
    }
}
