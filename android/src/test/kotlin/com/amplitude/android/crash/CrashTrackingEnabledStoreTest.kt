package com.amplitude.android.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CrashTrackingEnabledStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        context.getSharedPreferences("amplitude_crash_tracking", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `defaults to disabled`() {
        assertFalse(CrashTrackingEnabledStore(context).isEnabled())
    }

    @Test
    fun `persists enabled across store instances`() {
        CrashTrackingEnabledStore(context).setEnabled(true)
        assertTrue(CrashTrackingEnabledStore(context).isEnabled())
    }

    @Test
    fun `can disable after enabling`() {
        val store = CrashTrackingEnabledStore(context)
        store.setEnabled(true)
        store.setEnabled(false)
        assertFalse(CrashTrackingEnabledStore(context).isEnabled())
    }
}
