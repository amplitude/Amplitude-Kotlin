package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.MainDispatcherRule
import com.amplitude.android.utilities.createFakeAmplitude
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Identify
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.experiment.AmplitudeExperimentPlugin
import com.amplitude.experiment.ExperimentClient
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/** Exercises the published 1.17.0 plugin bytecode against this checkout's core. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReleasedExperimentIdentityTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `released Experiment fetches from both host and connector on a property change`() =
        runTest {
            val (amplitude, plugin, experiment) = startReleasedExperiment()

            amplitude.identify(Identify().set("plan", "pro"))
            advanceUntilIdle()

            // Experiment 1.17.0 fetches from both onIdentityChanged and its connector listener.
            // The next release trusts the host map, requires this analytics-core version, and
            // drops the connector listener. Expect one fetch once that release is pinned.
            verify(exactly = 2) {
                experiment.fetch(match { it.userProperties?.get("plan") == "pro" })
            }
            assertEquals("pro", amplitude.identity.userProperties["plan"])
            amplitude.remove(plugin)
        }

    @Test
    fun `released Experiment fetches option user id with Identify properties`() =
        runTest {
            val (amplitude, plugin, experiment) = startReleasedExperiment()

            amplitude.identify(Identify().set("plan", "pro"), EventOptions().apply { userId = "user" })
            advanceUntilIdle()

            // One host notification carries the new user id and properties together; the
            // remaining fetch is the 1.17.0 connector listener described above.
            verify(exactly = 2) { experiment.fetch(any()) }
            verify(exactly = 2) {
                experiment.fetch(match { it.userId == "user" && it.userProperties?.get("plan") == "pro" })
            }
            amplitude.remove(plugin)
        }

    private data class ReleasedExperiment(
        val amplitude: Amplitude,
        val plugin: AmplitudeExperimentPlugin,
        val experiment: ExperimentClient,
    )

    private suspend fun TestScope.startReleasedExperiment(): ReleasedExperiment {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            Configuration(
                apiKey = "api-key",
                context = context,
                instanceName = "released-experiment-${UUID.randomUUID()}",
                deviceId = "device",
                sessionId = 42L,
                autocapture = emptySet(),
                offline = true,
                storageProvider = InMemoryStorageProvider(),
                identifyInterceptStorageProvider = InMemoryStorageProvider(),
                identityStorageProvider = IMIdentityStorageProvider(),
                enableDiagnostics = false,
                enableAutocaptureRemoteConfig = false,
            )
        val amplitude = createFakeAmplitude(scheduler = testScheduler, configuration = configuration)
        amplitude.isBuilt.await()
        val plugin =
            AmplitudeExperimentPlugin(
                context,
                ExperimentConfig.builder()
                    .fetchOnStart(false)
                    .pollOnStart(false)
                    .automaticFetchOnAmplitudeIdentityChange(true)
                    .build(),
            )
        amplitude.add(plugin)
        // Replace transport with a mock after setup. The published plugin still owns its
        // real connector listener and lifecycle; this keeps fetch assertions deterministic.
        val experiment = mockk<ExperimentClient>(relaxed = true)
        plugin.javaClass.getDeclaredField("experimentClient").apply { isAccessible = true }
            .set(plugin, experiment)
        clearMocks(experiment)
        return ReleasedExperiment(amplitude, plugin, experiment)
    }
}
