package com.amplitude.android.internal.gestures

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.Window
import android.widget.FrameLayout
import android.widget.Switch
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.AutocaptureState
import com.amplitude.android.Constants.EventProperties.ACTION
import com.amplitude.android.Constants.EventProperties.TARGET_CLASS
import com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED
import com.amplitude.android.InteractionType.ElementInteraction
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.locators.AndroidViewTargetLocator
import com.amplitude.common.Logger
import com.google.android.material.R
import com.google.android.material.materialswitch.MaterialSwitch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        setUpCallback(control)
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

    @Test
    fun `should record a thumb drag on a MaterialSwitch through its SwitchCompat superclass`() {
        val context = ContextThemeWrapper(control.context, R.style.Theme_Material3_DayNight_NoActionBar)
        val root = FrameLayout(context)
        val materialSwitch = MaterialSwitch(context)
        root.addView(materialSwitch, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, 400, 100)
        // SwitchCompat positions its thumb drawable during drawing.
        materialSwitch.draw(Canvas())
        setUpCallback(root)

        var clicked = false
        materialSwitch.setOnClickListener { clicked = true }
        val thumb = requireNotNull(materialSwitch.thumbDrawable).bounds
        val startX = thumb.exactCenterX()
        val y = thumb.exactCenterY()
        val endX = requireNotNull(materialSwitch.trackDrawable).bounds.right.toFloat()
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        assertFalse(materialSwitch.isChecked)
        dispatch(MotionEvent.ACTION_DOWN, startX, y)
        // The first move enters dragging mode; the second moves the thumb.
        dispatch(MotionEvent.ACTION_MOVE, startX + touchSlop + 1, y)
        dispatch(MotionEvent.ACTION_MOVE, endX, y)
        dispatch(MotionEvent.ACTION_UP, endX, y)

        assertTrue(materialSwitch.isChecked)
        assertFalse("The thumb drag should not be handled as a click", clicked)
        verify(exactly = 1) {
            track(
                ELEMENT_INTERACTED,
                match {
                    it[ACTION] == GestureActions.PAN &&
                        it[TARGET_CLASS] == MaterialSwitch::class.java.canonicalName
                },
            )
        }
        verify(exactly = 1) { track(any(), any()) }
        verify(exactly = 0) { logger.error(any()) }
    }

    private fun setUpCallback(decorView: View) {
        every { delegate.dispatchTouchEvent(any()) } answers {
            decorView.dispatchTouchEvent(firstArg())
        }
        callback =
            AutocaptureWindowCallback(
                delegate = delegate,
                decorView = decorView,
                activityName = "TestActivity",
                track = track,
                viewTargetLocators = listOf(AndroidViewTargetLocator()),
                logger = logger,
                autocaptureStateProvider = { AutocaptureState(interactions = listOf(ElementInteraction)) },
            )
    }

    private fun dispatch(
        action: Int,
        x: Float,
        y: Float = 50f,
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
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
