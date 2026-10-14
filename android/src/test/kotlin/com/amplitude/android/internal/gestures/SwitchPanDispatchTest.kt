package com.amplitude.android.internal.gestures

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import android.widget.Switch
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.AutocaptureState
import com.amplitude.android.Constants.EventProperties.ACTION
import com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED
import com.amplitude.android.InteractionType.ElementInteraction
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.locators.AndroidViewTargetLocator
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SwitchPanDispatchTest {
    private val control = Switch(ApplicationProvider.getApplicationContext<Context>())
    private val track = mockk<TrackFunction>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val delegate = mockk<Window.Callback>(relaxed = true)
    private lateinit var callback: AutocaptureWindowCallback
    private val downTime = SystemClock.uptimeMillis()
    private var eventTime = downTime

    @Before
    fun setUp() {
        control.layout(0, 0, 400, 100)
        every { delegate.dispatchTouchEvent(any()) } answers {
            control.dispatchTouchEvent(firstArg())
        }
        callback =
            AutocaptureWindowCallback(
                delegate = delegate,
                decorView = control,
                activityName = "TestActivity",
                track = track,
                viewTargetLocators = listOf(AndroidViewTargetLocator()),
                logger = logger,
                autocaptureStateProvider = { AutocaptureState(interactions = listOf(ElementInteraction)) },
            )
    }

    @Test
    fun `should record a horizontal drag on an enabled switch`() {
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        dispatch(MotionEvent.ACTION_MOVE, 300f)
        dispatch(MotionEvent.ACTION_UP, 300f)

        verify(exactly = 1) { track(ELEMENT_INTERACTED, match { it[ACTION] == GestureActions.PAN }) }
        verify(exactly = 1) { track(any(), any()) }
        verify(exactly = 0) { logger.error(any()) }
    }

    @Test
    fun `should not record a drag on a disabled switch`() {
        control.isEnabled = false
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        dispatch(MotionEvent.ACTION_MOVE, 300f)
        dispatch(MotionEvent.ACTION_UP, 300f)

        verifyNoInteraction()
    }

    @Test
    fun `should not record a switch disabled during a drag`() {
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        dispatch(MotionEvent.ACTION_MOVE, 300f)
        control.isEnabled = false
        dispatch(MotionEvent.ACTION_UP, 300f)

        verifyNoInteraction()
    }

    private fun dispatch(
        action: Int,
        x: Float,
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, 50f, 0)
        try {
            callback.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
        eventTime += 20
    }

    private fun verifyNoInteraction() {
        verify(exactly = 0) {
            track(any(), any())
            logger.error(any())
        }
    }
}
