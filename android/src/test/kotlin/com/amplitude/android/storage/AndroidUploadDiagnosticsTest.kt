package com.amplitude.android.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amplitude.common.Logger
import com.amplitude.core.RestrictedAmplitudeFeature
import com.amplitude.core.utilities.Diagnostics
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@OptIn(RestrictedAmplitudeFeature::class)
@RunWith(RobolectricTestRunner::class)
class AndroidUploadDiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = UUID.randomUUID().toString()
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun storage() = AndroidStorageV2(
        storageKey = name,
        logger = mockk<Logger>(relaxed = true),
        sharedPreferences = preferences,
        storageDirectory = File(context.cacheDir, name),
        diagnostics = Diagnostics(),
    )

    @Test
    fun `pending request persists independently of deletion`() {
        val storage = storage()
        storage.uploadRequestPending = true
        val reopened = storage()
        assertTrue(reopened.uploadRequestPending)
        val batch = File(context.cacheDir, "$name-batch").apply { writeText("data") }
        assertTrue(storage.removeFile(batch.path))
        assertTrue(reopened.uploadRequestPending)
        assertFalse(storage.removeFile(batch.path))
        assertTrue(reopened.uploadRequestPending)
        reopened.uploadRequestPending = false
        assertFalse(storage.uploadRequestPending)
    }
}
