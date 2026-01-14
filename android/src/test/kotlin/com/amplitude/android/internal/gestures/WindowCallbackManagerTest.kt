package com.amplitude.android.internal.gestures

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import com.amplitude.android.AutocaptureState
import com.amplitude.android.internal.TrackFunction
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class WindowCallbackManagerTest {
    private val track = mockk<TrackFunction>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val autocaptureState = AutocaptureState(interactions = emptyList())

    @Test
    fun `wraps window callback for activity window`() {
        val activity = mockk<Activity>(relaxed = true)
        val window = mockk<Window>(relaxed = true)
        val decorView = mockk<View>(relaxed = true)
        val originalCallback = mockk<Window.Callback>(relaxed = true)

        every { window.context } returns activity
        every { window.callback } returns originalCallback

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureState = autocaptureState,
                logger = logger,
            )

        sut.start()
        sut.wrapWindowForTesting(window, decorView)

        verify { window.callback = any<AutocaptureWindowCallback>() }
    }

    @Test
    fun `skips window when activity cannot be found`() {
        val context = mockk<Context>(relaxed = true)
        val window = mockk<Window>(relaxed = true)
        val decorView = mockk<View>(relaxed = true)

        every { window.context } returns context

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureState = autocaptureState,
                logger = logger,
            )

        sut.start()
        sut.wrapWindowForTesting(window, decorView)

        verify(exactly = 0) { window.callback = any<AutocaptureWindowCallback>() }
        verify { logger.debug("Unable to get Activity from window context, skipping window") }
    }

    @Test
    fun `finds activity through ContextWrapper chain`() {
        val activity = mockk<Activity>(relaxed = true)
        val wrapper = mockk<ContextWrapper>(relaxed = true)
        val window = mockk<Window>(relaxed = true)
        val decorView = mockk<View>(relaxed = true)
        val originalCallback = mockk<Window.Callback>(relaxed = true)

        // Activity wrapped in ContextWrapper (like dialogs do)
        every { wrapper.baseContext } returns activity
        every { window.context } returns wrapper
        every { window.callback } returns originalCallback

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureState = autocaptureState,
                logger = logger,
            )

        sut.start()
        sut.wrapWindowForTesting(window, decorView)

        verify { window.callback = any<AutocaptureWindowCallback>() }
    }

    @Test
    fun `does not double-wrap same window`() {
        val activity = mockk<Activity>(relaxed = true)
        val window = mockk<Window>(relaxed = true)
        val decorView = mockk<View>(relaxed = true)
        val originalCallback = mockk<Window.Callback>(relaxed = true)

        every { window.context } returns activity
        every { window.callback } returns originalCallback

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureState = autocaptureState,
                logger = logger,
            )

        sut.start()
        sut.wrapWindowForTesting(window, decorView)
        sut.wrapWindowForTesting(window, decorView)

        verify(exactly = 1) { window.callback = any<AutocaptureWindowCallback>() }
    }

    @Test
    fun `unwraps window and restores original callback`() {
        val activity = mockk<Activity>(relaxed = true)
        val window = mockk<Window>(relaxed = true)
        val decorView = mockk<View>(relaxed = true)
        val originalCallback = mockk<Window.Callback>(relaxed = true)

        every { window.context } returns activity
        every { window.callback } returns originalCallback

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureState = autocaptureState,
                logger = logger,
            )

        sut.start()

        // Wrap
        sut.wrapWindowForTesting(window, decorView)

        // Simulate that window.callback returns our wrapper now
        every { window.callback } returns mockk<AutocaptureWindowCallback>(relaxed = true)

        // Unwrap
        sut.unwrapWindowForTesting(window)

        verify { window.callback = originalCallback }
    }
}
