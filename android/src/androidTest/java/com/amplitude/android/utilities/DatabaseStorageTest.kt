package com.amplitude.android.utilities

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseStorageTest {
    var databaseStorage: DatabaseStorage? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        databaseStorage = DatabaseStorage(appContext)
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        Assert.assertEquals("com.amplitude.android.test", appContext.packageName)
    }
}
