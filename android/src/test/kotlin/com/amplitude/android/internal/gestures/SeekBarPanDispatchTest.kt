package com.amplitude.android.internal.gestures

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.Window
import android.widget.FrameLayout
import android.widget.SeekBar
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SeekBarPanDispatchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = InterceptingLayout(context)
    private val seekBar = SeekBar(context)
    private val track = mockk<TrackFunction>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val delegate = mockk<Window.Callback>(relaxed = true)
    private lateinit var callback: AutocaptureWindowCallback
    private val downTime = SystemClock.uptimeMillis()
    private var eventTime = downTime

    @Before
    fun setUp() {
        seekBar.setPadding(0, 0, 0, 0)
        seekBar.max = 100
        seekBar.progress = 50
        root.addView(seekBar)
        root.layout(0, 0, 400, 100)
        seekBar.layout(0, 0, 400, 100)
        every { delegate.dispatchTouchEvent(any()) } answers {
            root.dispatchTouchEvent(firstArg())
        }
        callback =
            AutocaptureWindowCallback(
                delegate = delegate,
                decorView = root,
                activityName = "TestActivity",
                track = track,
                viewTargetLocators = listOf(AndroidViewTargetLocator()),
                logger = logger,
                autocaptureStateProvider = { AutocaptureState(interactions = listOf(ElementInteraction)) },
            )
    }

    @Test
    fun `should record a drag before release clears the pressed state`() {
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        assertFalse(seekBar.isPressed)
        dispatch(MotionEvent.ACTION_MOVE, 300f)
        assertTrue(seekBar.isPressed)
        dispatch(MotionEvent.ACTION_UP, 300f)

        assertFalse(seekBar.isPressed)
        verifyPan()
    }

    @Test
    fun `should not record a drag cancelled by a parent before release`() {
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        dispatch(MotionEvent.ACTION_MOVE, 300f)
        assertTrue(seekBar.isPressed)

        root.requestDisallowInterceptTouchEvent(false)
        root.interceptMoves = true
        dispatch(MotionEvent.ACTION_MOVE, 320f)
        assertFalse(seekBar.isPressed)
        dispatch(MotionEvent.ACTION_UP, 320f)

        verifyNoInteraction()
    }

    @Test
    fun `should not record a drag on a disabled slider`() {
        seekBar.isEnabled = false
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        dispatch(MotionEvent.ACTION_MOVE, 300f)
        dispatch(MotionEvent.ACTION_UP, 300f)

        verifyNoInteraction()
    }

    @Test
    fun `should not record a slider disabled during a drag`() {
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        dispatch(MotionEvent.ACTION_MOVE, 300f)
        assertTrue(seekBar.isPressed)
        seekBar.isEnabled = false
        dispatch(MotionEvent.ACTION_UP, 300f)

        verifyNoInteraction()
    }

    @Test
    fun `should record a drag that returns to the original value`() {
        val originalProgress = seekBar.progress
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        dispatch(MotionEvent.ACTION_MOVE, 300f)
        assertTrue(seekBar.progress > originalProgress)
        dispatch(MotionEvent.ACTION_MOVE, 200f)
        dispatch(MotionEvent.ACTION_UP, 200f)

        assertEquals(originalProgress, seekBar.progress)
        verifyPan()
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

    private fun verifyPan() {
        verify(exactly = 1) { track(ELEMENT_INTERACTED, match { it[ACTION] == GestureActions.PAN }) }
        verify(exactly = 1) { track(any(), any()) }
        verify(exactly = 0) { logger.error(any()) }
    }

    private fun verifyNoInteraction() {
        verify(exactly = 0) {
            track(any(), any())
            logger.error(any())
        }
    }

    private class InterceptingLayout(context: Context) : FrameLayout(context) {
        var interceptMoves = false

        override fun shouldDelayChildPressedState(): Boolean = true

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
            interceptMoves && event.actionMasked == MotionEvent.ACTION_MOVE

        override fun onTouchEvent(event: MotionEvent): Boolean = true
    }
}
