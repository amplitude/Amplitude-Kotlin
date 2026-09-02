package com.amplitude.android.internal.gestures

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import android.view.View
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.AutocaptureState
import com.amplitude.android.internal.TrackFunction
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class WindowCallbackManagerTest {
    private val track: TrackFunction = { _, _ -> }
    private val logger = mockk<Logger>(relaxed = true)
    private val autocaptureState = AutocaptureState(interactions = emptyList())
    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `wraps window callback for activity window`() {
        val activity = mockk<Activity>(relaxed = true)
        val window = mockk<Window>(relaxed = true)
        val decorView = mockk<View>(relaxed = true)
        val originalCallback = mockk<Window.Callback>(relaxed = true)

        every { window.context } returns activity
        every { window.callback } returns originalCallback
        every { decorView.context } returns appContext

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureStateProvider = { autocaptureState },
                logger = logger,
            )

        sut.start()
        shadowOf(Looper.getMainLooper()).idle()
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
                autocaptureStateProvider = { autocaptureState },
                logger = logger,
            )

        sut.start()
        shadowOf(Looper.getMainLooper()).idle()
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
        every { decorView.context } returns appContext

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureStateProvider = { autocaptureState },
                logger = logger,
            )

        sut.start()
        shadowOf(Looper.getMainLooper()).idle()
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
        every { decorView.context } returns appContext

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureStateProvider = { autocaptureState },
                logger = logger,
            )

        sut.start()
        shadowOf(Looper.getMainLooper()).idle()
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
        every { decorView.context } returns appContext

        val sut =
            WindowCallbackManager(
                track = track,
                frustrationDetector = null,
                autocaptureStateProvider = { autocaptureState },
                logger = logger,
            )

        sut.start()
        shadowOf(Looper.getMainLooper()).idle()

        // Wrap
        val wrapper = slot<Window.Callback>()
        every { window.callback = capture(wrapper) } answers {}
        sut.wrapWindowForTesting(window, decorView)

        // Simulate that window.callback returns our wrapper now
        every { window.callback } returns wrapper.captured

        // Unwrap
        sut.unwrapWindowForTesting(window)

        verify { window.callback = originalCallback }
    }

    @Test
    fun `leaves the chain alone when another manager wrapped on top`() {
        val activity = mockk<Activity>(relaxed = true)
        val window = mockk<Window>(relaxed = true)
        val decorView = mockk<View>(relaxed = true)
        val originalCallback = mockk<Window.Callback>(relaxed = true)

        every { window.context } returns activity
        every { window.callback } returns originalCallback
        every { decorView.context } returns appContext

        val first = newManager()
        val second = newManager()
        first.start()
        second.start()
        shadowOf(Looper.getMainLooper()).idle()

        val firstWrapper = slot<Window.Callback>()
        every { window.callback = capture(firstWrapper) } answers {}
        first.wrapWindowForTesting(window, decorView)

        // The second manager wraps the first manager's callback.
        every { window.callback } returns firstWrapper.captured
        val secondWrapper = slot<Window.Callback>()
        every { window.callback = capture(secondWrapper) } answers {}
        second.wrapWindowForTesting(window, decorView)
        every { window.callback } returns secondWrapper.captured

        first.unwrapWindowForTesting(window)

        // Restoring the original here would drop the second manager's wrapper.
        verify(exactly = 0) { window.callback = originalCallback }

        // The first manager spliced itself out, so the second one restores the real original.
        second.unwrapWindowForTesting(window)
        verify { window.callback = originalCallback }
    }

    private fun newManager() =
        WindowCallbackManager(
            track = track,
            frustrationDetector = null,
            autocaptureStateProvider = { autocaptureState },
            logger = logger,
        )
}
