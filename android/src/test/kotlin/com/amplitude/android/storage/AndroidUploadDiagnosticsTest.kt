package com.amplitude.android.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.common.Logger
import com.amplitude.core.Configuration
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.diagnostics.DiagnosticsClient
import com.amplitude.core.diagnostics.DiagnosticsClientProvider
import com.amplitude.core.events.BaseEvent
import com.amplitude.core.platform.EventPipeline
import com.amplitude.core.utilities.Diagnostics
import com.amplitude.core.utilities.http.SuccessResponse
import com.amplitude.core.utilities.http.TimeoutResponse
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@OptIn(RestrictedAmplitudeFeature::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidUploadDiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = UUID.randomUUID().toString()
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val client = mockk<DiagnosticsClient>(relaxed = true)

    private fun storage(enabled: Boolean = true) =
        AndroidStorageV2(
            name,
            mockk<Logger>(relaxed = true),
            preferences,
            File(context.cacheDir, name),
            Diagnostics(),
            DiagnosticsClientProvider { client },
            sampleUploadAttempts = enabled,
        )

    @Test
    fun `counts and clears leftover phases once`() {
        preferences.edit()
            .putString("upload_attempt_sample.first", "network_callback")
            .putString("upload_attempt_sample.second", "cleanup")
            .commit()
        storage().readEventsContent()
        storage().readEventsContent()
        verify(exactly = 1) { client.increment("analytics.upload.missed_network_callback", 1) }
        verify(exactly = 1) { client.increment("analytics.upload.missed_cleanup", 1) }
    }

    @Test
    fun `queue scans do not recover twice and success clears marker`() = runTest {
        val storage = storage()
        storage.writeEvent(BaseEvent().apply { eventType = "test" })
        storage.rollover()
        val path = storage.readEventsContent().single() as String
        val events = storage.getEventsString(path)
        val key = "upload_attempt_sample." + File(path).name
        assertTrue(preferences.getString(key, "") == "network_callback")
        storage.readEventsContent()
        verify(exactly = 0) { client.increment(any(), any()) }
        storage.getResponseHandler(mockk<EventPipeline>(), Configuration(apiKey = "test"), this, StandardTestDispatcher(testScheduler))
            .handle(SuccessResponse(), path, events)
        runCurrent()
        assertFalse(File(path).exists())
        assertFalse(preferences.contains(key))
    }

    @Test
    fun `failure response clears pending network callback marker`() = runTest {
        val storage = storage()
        storage.writeEvent(BaseEvent().apply { eventType = "test" })
        storage.rollover()
        val path = storage.readEventsContent().single() as String
        val events = storage.getEventsString(path)
        storage.getResponseHandler(mockk<EventPipeline>(), Configuration(apiKey = "test"), this, StandardTestDispatcher(testScheduler))
            .handle(TimeoutResponse(), path, events)
        assertFalse(preferences.contains("upload_attempt_sample." + File(path).name))
        assertTrue(File(path).exists())
    }

    @Test
    fun `disabled diagnostics does not write markers`() = runTest {
        val storage = storage(enabled = false)
        storage.writeEvent(BaseEvent().apply { eventType = "test" })
        storage.rollover()
        storage.getEventsString(storage.readEventsContent().single())
        assertFalse(preferences.all.keys.any { it.startsWith("upload_attempt_sample.") })
    }
}
