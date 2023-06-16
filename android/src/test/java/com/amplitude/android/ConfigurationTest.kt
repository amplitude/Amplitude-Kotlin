package com.amplitude.android

import android.app.Application
import android.content.Context
import com.amplitude.android.plugins.AndroidLifecyclePlugin
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConfigurationTest {
    private var context: Context? = null

    @BeforeEach
    fun setUp() {
        context = mockk<Application>(relaxed = true)
        mockkStatic(AndroidLifecyclePlugin::class)
    }

    @Test
    fun configuration_init_isValid() {
        val configuration = Configuration("test-apikey", context!!)
        Assertions.assertTrue(configuration.isValid())
    }

    @Suppress("DEPRECATION")
    @Test
    fun configuration_allows_propertyUpdate() {
        val configuration = Configuration("test-apikey", context!!)
        Assertions.assertTrue(configuration.trackingSessionEvents)
        Assertions.assertTrue(configuration.defaultTracking.sessions)
        Assertions.assertFalse(configuration.defaultTracking.appLifecycles)
        Assertions.assertFalse(configuration.defaultTracking.deepLinks)
        Assertions.assertFalse(configuration.defaultTracking.screenViews)
        configuration.trackingSessionEvents = false
        configuration.defaultTracking.sessions = false
        configuration.defaultTracking.appLifecycles = true
        configuration.defaultTracking.deepLinks = true
        configuration.defaultTracking.screenViews = true
        Assertions.assertFalse(configuration.trackingSessionEvents)
        Assertions.assertFalse(configuration.defaultTracking.sessions)
        Assertions.assertTrue(configuration.defaultTracking.appLifecycles)
        Assertions.assertTrue(configuration.defaultTracking.deepLinks)
        Assertions.assertTrue(configuration.defaultTracking.screenViews)
    }

    @Test
    fun configuration_defaultTracking_quick_update() {
        val configuration = Configuration(
            "test-apikey",
            context!!,
            defaultTracking = DefaultTrackingOptions.ALL
        )
        Assertions.assertTrue(configuration.defaultTracking.sessions)
        Assertions.assertTrue(configuration.defaultTracking.appLifecycles)
        Assertions.assertTrue(configuration.defaultTracking.deepLinks)
        Assertions.assertTrue(configuration.defaultTracking.screenViews)

        configuration.defaultTracking = DefaultTrackingOptions.NONE
        Assertions.assertFalse(configuration.defaultTracking.sessions)
        Assertions.assertFalse(configuration.defaultTracking.appLifecycles)
        Assertions.assertFalse(configuration.defaultTracking.deepLinks)
        Assertions.assertFalse(configuration.defaultTracking.screenViews)
    }
}
