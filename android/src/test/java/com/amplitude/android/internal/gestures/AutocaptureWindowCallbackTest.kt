package com.amplitude.android.internal.gestures

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Window
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class AutocaptureWindowCallbackTest {
    class Fixture {
        val activity = mockk<Activity>()
        val delegate = mockk<Window.Callback>(relaxed = true)
        val track = mockk<(String, Map<String, Any?>) -> Unit>()
        val gestureDetector = mockk<GestureDetector>()
        val gestureListener = mockk<AutocaptureGestureListener>()
        val motionEventCopy = mockk<MotionEvent>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)

        internal fun getSut(): AutocaptureWindowCallback {
            return AutocaptureWindowCallback(
                delegate,
                activity,
                track,
                logger,
                object : AutocaptureWindowCallback.MotionEventObtainer {
                    override fun obtain(origin: MotionEvent): MotionEvent {
                        val actionMasked = origin.actionMasked
                        every { motionEventCopy.actionMasked } returns actionMasked
                        return motionEventCopy
                    }
                },
                gestureListener,
                gestureDetector,
            )
        }
    }

    private val fixture = Fixture()

    @Test
    fun `delegates the events to the gesture detector`() {
        val event = mockk<MotionEvent>(relaxed = true)
        val sut = fixture.getSut()

        sut.dispatchTouchEvent(event)

        verify { fixture.gestureDetector.onTouchEvent(fixture.motionEventCopy) }
        verify { fixture.motionEventCopy.recycle() }
    }

    @Test
    fun `nullable event is ignored`() {
        val sut = fixture.getSut()

        sut.dispatchTouchEvent(null)

        verify(exactly = 0) { fixture.gestureDetector.onTouchEvent(any()) }
    }
}
