package com.amplitude.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.utilities.createFakeAmplitude
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AndroidAmplitudeContextTest {
    @Test
    fun `platformContext is the application context`() =
        runTest {
            val amplitude = createFakeAmplitude(server = null, scheduler = testScheduler)
            amplitude.isBuilt.await()
            advanceUntilIdle()

            val appContext = ApplicationProvider.getApplicationContext<Context>().applicationContext
            assertSame(appContext, amplitude.amplitudeContext.platformContext)
        }
}
