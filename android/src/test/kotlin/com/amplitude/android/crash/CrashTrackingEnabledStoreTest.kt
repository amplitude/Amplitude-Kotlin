package com.amplitude.android.crash

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        assertFalse(CrashTrackingEnabledStore(context, "test-instance").isEnabled())
    }

    @Test
    fun `persists enabled across store instances`() {
        CrashTrackingEnabledStore(context, "test-instance").setEnabled(true)
        assertTrue(CrashTrackingEnabledStore(context, "test-instance").isEnabled())
    }

    @Test
    fun `should persist the decision before returning even if the process exits immediately`() {
        var pending = false
        var persisted = false
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putBoolean(any(), any()) } answers {
            pending = secondArg()
            editor
        }
        every { editor.commit() } answers {
            persisted = pending
            true
        }
        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        val processContext = mockk<Context>()
        every { processContext.applicationContext } returns processContext
        every { processContext.getSharedPreferences(any(), any()) } returns prefs

        CrashTrackingEnabledStore(processContext, "test-instance").setEnabled(true)

        // A new process can recover only the value committed to disk, not a pending apply().
        assertTrue(persisted)
        CrashTrackingEnabledStore(processContext, "test-instance").setEnabled(false)
        assertFalse(persisted)
    }

    @Test
    fun `can disable after enabling`() {
        val store = CrashTrackingEnabledStore(context, "test-instance")
        store.setEnabled(true)
        store.setEnabled(false)
        assertFalse(CrashTrackingEnabledStore(context, "test-instance").isEnabled())
    }

    @Test
    fun `disabled named instance does not overwrite another instance's enabled decision`() {
        CrashTrackingEnabledStore(context, "enabled-instance").setEnabled(true)
        CrashTrackingEnabledStore(context, "disabled-instance").setEnabled(false)

        assertTrue(CrashTrackingEnabledStore(context, "enabled-instance").isEnabled())
        assertFalse(CrashTrackingEnabledStore(context, "disabled-instance").isEnabled())
        assertFalse(CrashTrackingEnabledStore(context, "new-instance").isEnabled())
    }
}
