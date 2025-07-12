package com.amplitude.android.utilities

import android.app.Application
import com.amplitude.MainDispatcherRule
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.utilities.InMemoryStorageProvider
import com.amplitude.id.IMIdentityStorageProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AndroidLoggerProviderTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @JvmField
    @Rule
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun androidLoggerProvider_getLogger_returnsSingletonInstance() {
        val testApiKey = "test-123"
        val context = mockk<Application>(relaxed = true)
        every { context.getDir(any(), any()) } returns temporaryFolder.newFolder("testDir")

        val amplitude =
            Amplitude(
                Configuration(
                    testApiKey,
                    context = context,
                    identifyInterceptStorageProvider = InMemoryStorageProvider(),
                    identityStorageProvider = IMIdentityStorageProvider(),
                ),
            )
        val loggerProvider = AndroidLoggerProvider()
        val logger1 = loggerProvider.getLogger(amplitude)
        val logger2 = loggerProvider.getLogger(amplitude)

        assertEquals(logger1, logger2)
    }
}
