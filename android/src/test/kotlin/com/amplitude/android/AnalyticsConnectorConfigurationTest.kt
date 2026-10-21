package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.FakeAndroidAmplitude
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.analytics.connector.AnalyticsConnector
import com.amplitude.analytics.connector.Identity
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Identify
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AnalyticsConnectorConfigurationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `should install legacy connector plugins`() = verifyConnectorPlugins()

    @Test
    fun `should install legacy connector plugins when builder is passed directly`() =
        verifyConnectorPlugins(passBuilderDirectly = true)

    @Test
    fun `connector never pairs identify properties with the previous user id`() =
        runTest {
            val builder = ConfigurationBuilder("api-key", ApplicationProvider.getApplicationContext<Context>())
            builder.apply {
                instanceName = "connector-order-${UUID.randomUUID()}"
                autocapture = emptySet()
                offline = true
                storageProvider = InMemoryStorageProvider()
                identifyInterceptStorageProvider = InMemoryStorageProvider()
                identityStorageProvider = IMIdentityStorageProvider()
                enableDiagnostics = false
                enableAutocaptureRemoteConfig = false
            }
            // Run the pipeline inline so the Identify reaches the connector during identify().
            val amplitude =
                FakeAndroidAmplitude(builder.build(), androidTestDispatcher = UnconfinedTestDispatcher(testScheduler))
            amplitude.isBuilt.await()
            amplitude.setUserId("previous")
            advanceUntilIdle()
            val connectorIdentities = mutableListOf<Identity>()
            AnalyticsConnector.getInstance(builder.instanceName).identityStore
                .addIdentityListener { connectorIdentities += it }

            amplitude.identify(Identify().set("plan", "pro"), EventOptions().apply { userId = "user" })
            advanceUntilIdle()

            assertEquals("user", connectorIdentities.last().userId)
            assertEquals("pro", connectorIdentities.last().userProperties["plan"])
            assertTrue(connectorIdentities.toString(), connectorIdentities.none { it.userId == "previous" && it.userProperties["plan"] == "pro" })
        }

    private fun verifyConnectorPlugins(
        passBuilderDirectly: Boolean = false,
    ) =
        runTest {
            val builder = ConfigurationBuilder("api-key", ApplicationProvider.getApplicationContext<Context>())
            builder.apply {
                instanceName = "connector-installed-${UUID.randomUUID()}"
                autocapture = emptySet()
                offline = true
                storageProvider = InMemoryStorageProvider()
                identifyInterceptStorageProvider = InMemoryStorageProvider()
                identityStorageProvider = IMIdentityStorageProvider()
                enableDiagnostics = false
                enableAutocaptureRemoteConfig = false
            }
            val configuration = if (passBuilderDirectly) builder else builder.build()
            val amplitude = createFakeAmplitude(scheduler = testScheduler, configuration = configuration)
            amplitude.isBuilt.await()

            val connectorPlugins = amplitude.plugins(UniversalPlugin::class.java)
                .filter { it.javaClass.simpleName.startsWith("AnalyticsConnector") }

            assertEquals(2, connectorPlugins.size)
        }
}
