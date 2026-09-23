package com.amplitude.unified

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.amplitude.core.Amplitude
import com.amplitude.core.platform.Plugin
import com.amplitude.core.platform.UniversalPlugin
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
internal class AmplitudeUnifiedReplacementTest {
    @Test
    fun `replaced wrapper must not install a blade after retirement cleanup`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val old = AtomicReference<Amplitude>()
        val releaseExperiment = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first =
                executor.submit<AmplitudeUnified> {
                    val builder = UnifiedConfigurationBuilder("api-key", application)
                    builder.analytics.instanceName = "unified-replacement"
                    builder.analytics.offline = true
                    builder.analytics.autocapture = emptySet()
                    AmplitudeUnified(
                        builder.buildSnapshot(),
                        object : UnifiedPluginFactory {
                            override fun sessionReplay(configuration: SessionReplayConfiguration): UniversalPlugin =
                                CapturePlugin("com.amplitude.android.sessionreplay", old)

                            override fun experiment(configuration: ExperimentConfiguration): UniversalPlugin {
                                check(releaseExperiment.await(20, TimeUnit.SECONDS))
                                return CapturePlugin("com.amplitude.experiment", AtomicReference())
                            }
                        },
                    )
                }
            waitUntil { old.get()?.plugin("com.amplitude.android.sessionreplay") != null }
            val replaced = old.get()
            val replacementBuilder = UnifiedConfigurationBuilder("api-key", application)
            replacementBuilder.analytics.instanceName = "unified-replacement"
            replacementBuilder.analytics.offline = true
            replacementBuilder.analytics.autocapture = emptySet()
            replacementBuilder.sessionReplay.enabled = false
            replacementBuilder.experiment.enabled = false
            AmplitudeUnified(replacementBuilder)
            waitUntil { replaced.plugin("com.amplitude.android.sessionreplay") == null }

            releaseExperiment.countDown()
            val retiredWrapper = first.get(10, TimeUnit.SECONDS)
            assertNull(retiredWrapper.plugin("com.amplitude.experiment"))
        } finally {
            releaseExperiment.countDown()
            executor.shutdownNow()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!condition()) {
            check(System.nanoTime() < deadline)
            Thread.sleep(10)
        }
    }
}

private class CapturePlugin(
    override val name: String,
    private val client: AtomicReference<Amplitude>,
) : Plugin {
    override val type: Plugin.Type = Plugin.Type.Enrichment
    override lateinit var amplitude: Amplitude

    override fun setup(amplitude: Amplitude) {
        super.setup(amplitude)
        client.set(amplitude)
    }
}
