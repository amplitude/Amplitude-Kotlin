package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.core.platform.UniversalPlugin
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
