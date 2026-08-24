package com.amplitude.android.diagnostics

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidDiagnosticsContextProviderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `app release is false for a debuggable application`() {
        val originalFlags = context.applicationInfo.flags
        try {
            context.applicationInfo.flags = originalFlags or ApplicationInfo.FLAG_DEBUGGABLE
            assertFalse(AndroidDiagnosticsContextProvider(context).getContextInfo().appRelease)
        } finally {
            context.applicationInfo.flags = originalFlags
        }
    }

    @Test
    fun `app release is true for a non-debuggable application`() {
        val originalFlags = context.applicationInfo.flags
        try {
            context.applicationInfo.flags = originalFlags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
            assertTrue(AndroidDiagnosticsContextProvider(context).getContextInfo().appRelease)
        } finally {
            context.applicationInfo.flags = originalFlags
        }
    }
}
