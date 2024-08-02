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
        Assertions.assertFalse(AutocaptureOption.SESSIONS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.APP_LIFECYCLES in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.DEEP_LINKS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.SCREEN_VIEWS in configuration.autocapture)
        Assertions.assertFalse(AutocaptureOption.ELEMENT_INTERACTIONS in configuration.autocapture)
    }

    @OptIn(ExperimentalAmplitudeFeature::class)
    @Test
    fun configuration_defaultTracking_quick_update() {
        val configuration = Configuration(
            "test-apikey",
            context!!,
            autocapture = autocaptureOptions {
                +sessions
                +appLifecycles
                +deepLinks
                +screenViews
                +elementInteractions
            }
        )
        Assertions.assertTrue(AutocaptureOption.SESSIONS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.APP_LIFECYCLES in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.DEEP_LINKS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.SCREEN_VIEWS in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.ELEMENT_INTERACTIONS in configuration.autocapture)

        configuration.autocapture.clear()
        Assertions.assertTrue(AutocaptureOption.SESSIONS !in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.APP_LIFECYCLES !in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.DEEP_LINKS !in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.SCREEN_VIEWS !in configuration.autocapture)
        Assertions.assertTrue(AutocaptureOption.ELEMENT_INTERACTIONS !in configuration.autocapture)
    }
}
