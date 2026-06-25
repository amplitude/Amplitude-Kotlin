package com.amplitude.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import com.amplitude.MainDispatcherRule
import com.amplitude.android.plugins.AndroidContextPlugin
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import com.amplitude.android.utilities.ActivityLifecycleObserver
import com.amplitude.android.utilities.setupMockAndroidContext
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.State
import com.amplitude.core.platform.plugins.AmplitudeDestination
import com.amplitude.core.platform.plugins.GetAmpliExtrasPlugin
import com.amplitude.core.utilities.ConsoleLoggerProvider
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import com.amplitude.id.IdentityConfiguration
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import com.amplitude.core.Amplitude as CoreAmplitude

/**
 * Regression tests for #416 — [Amplitude.autocaptureManager] must be assigned in [Amplitude.build]
 * before [com.amplitude.core.Amplitude.buildInternal] can run on a background thread while the
 * constructor is still finishing subclass field initialization.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AmplitudeAutocaptureInitOrderTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())

    @Test
    fun `by lazy autocaptureManager NPEs when buildInternal runs before property init`() {
        val configuration = createAutocaptureConfiguration()
        val pool = Executors.newCachedThreadPool()
        val amplitudeDispatcher = pool.asCoroutineDispatcher()

        val releasePropertyInit = CountDownLatch(1)
        val buildInternalEntered = CountDownLatch(1)
        val accessError = AtomicReference<Throwable?>(null)

        val constructThread =
            Thread {
                LazyInitRaceFixture(
                    configuration = configuration,
                    amplitudeDispatcher = amplitudeDispatcher,
                    releasePropertyInit = releasePropertyInit,
                    buildInternalEntered = buildInternalEntered,
                    accessError = accessError,
                )
            }

        constructThread.start()
        assertTrue(buildInternalEntered.await(5, TimeUnit.SECONDS))
        releasePropertyInit.countDown()
        constructThread.join(30_000)

        pool.shutdown()
        pool.awaitTermination(5, TimeUnit.SECONDS)

        val error = accessError.get()
        assertTrue(
            "Expected NPE from null Lazy delegate, got: $error",
            error is NullPointerException,
        )
    }

    @Test
    fun `production Amplitude assigns autocaptureManager before buildInternal accesses it`() =
        runBlocking {
            val configuration = createAutocaptureConfiguration()
            val pool = Executors.newCachedThreadPool()
            val amplitudeDispatcher = pool.asCoroutineDispatcher()
            val accessError = AtomicReference<Throwable?>(null)

            try {
                val amplitude =
                    ProductionInitOrderProbe(
                        configuration = configuration,
                        amplitudeDispatcher = amplitudeDispatcher,
                        accessError = accessError,
                    )
                amplitude.isBuilt.await()
            } finally {
                pool.shutdown()
                pool.awaitTermination(5, TimeUnit.SECONDS)
            }

            assertNull(
                "autocaptureManager must be assigned in build() before buildInternal can access it (#416)",
                accessError.get(),
            )
        }

    private fun createAutocaptureConfiguration(): Configuration {
        setupMockAndroidContext()
        val context = mockk<Application>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        val dirNameSlot = slot<String>()
        every { context.getDir(capture(dirNameSlot), any()) } answers {
            File("/tmp/amplitude-kotlin/${dirNameSlot.captured}")
        }

        return Configuration(
            apiKey = "api-key",
            context = context,
            instanceName = INSTANCE_NAME,
            storageProvider = InMemoryStorageProvider(),
            autocapture =
                setOf(
                    AutocaptureOption.SESSIONS,
                    AutocaptureOption.APP_LIFECYCLES,
                ),
            loggerProvider = ConsoleLoggerProvider(),
            identifyInterceptStorageProvider = InMemoryStorageProvider(),
            identityStorageProvider = IMIdentityStorageProvider(),
        )
    }

    /**
     * Minimal reproduction of the #416 init-order bug: a `by lazy` property on a [CoreAmplitude]
     * subclass while [buildInternal] is already running on a background thread before the
     * subclass property-initialization phase reaches the lazy delegate field.
     */
    @OptIn(RestrictedAmplitudeFeature::class)
    private class LazyInitRaceFixture(
        configuration: Configuration,
        amplitudeDispatcher: CoroutineDispatcher,
        private val releasePropertyInit: CountDownLatch,
        private val buildInternalEntered: CountDownLatch,
        private val accessError: AtomicReference<Throwable?>,
    ) : CoreAmplitude(
            configuration = configuration,
            store = State(),
            amplitudeScope = CoroutineScope(SupervisorJob()),
            amplitudeDispatcher = amplitudeDispatcher,
            networkIODispatcher = amplitudeDispatcher,
            storageIODispatcher = amplitudeDispatcher,
        ) {
        private val activityLifecycleCallbacks = ActivityLifecycleObserver()

        // Runs before the lazy delegate below is installed; holds the constructor open while
        // buildInternal may already be executing on the amplitude dispatcher thread.
        @Suppress("unused")
        private val blockPropertyInit =
            run {
                releasePropertyInit.await(5, TimeUnit.SECONDS)
                Unit
            }

        internal val autocaptureManager: AutocaptureManager by lazy {
            val androidConfig = configuration as Configuration
            AutocaptureManager(
                initialAutocapture = androidConfig.autocapture,
                initialInteractionsOptions = androidConfig.interactionsOptions,
                remoteConfigClient = null,
                logger = logger,
                diagnosticsClient = null,
            )
        }

        override fun createTimeline(): com.amplitude.core.platform.Timeline {
            return com.amplitude.core.platform.Timeline().also { it.amplitude = this }
        }

        override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
            buildInternalEntered.countDown()
            try {
                // Same access path as AndroidLifecyclePlugin.setup.
                autocaptureManager.state.value
            } catch (error: Throwable) {
                accessError.compareAndSet(null, error)
            }
            createIdentityContainer(identityConfiguration)
            add(AndroidContextPlugin())
            add(GetAmpliExtrasPlugin())
            add(AndroidLifecyclePlugin(activityLifecycleCallbacks))
            add(AmplitudeDestination())
        }
    }

    /**
     * Production [Amplitude] subclass that probes [autocaptureManager] at the very start of
     * [buildInternal], before [AndroidLifecyclePlugin] setup can run.
     */
    private class ProductionInitOrderProbe(
        configuration: Configuration,
        amplitudeDispatcher: CoroutineDispatcher,
        private val accessError: AtomicReference<Throwable?>,
    ) : Amplitude(
            configuration = configuration,
            state = State(),
            amplitudeScope = CoroutineScope(SupervisorJob()),
            amplitudeDispatcher = amplitudeDispatcher,
            networkIODispatcher = amplitudeDispatcher,
            storageIODispatcher = amplitudeDispatcher,
        ) {
        override suspend fun buildInternal(identityConfiguration: IdentityConfiguration) {
            try {
                autocaptureManager.state.value
            } catch (error: Throwable) {
                accessError.compareAndSet(null, error)
                return
            }
            super.buildInternal(identityConfiguration)
        }
    }

    companion object {
        private const val INSTANCE_NAME = "autocapture-init-order-test"
    }
}
