package com.amplitude.android.internal.gestures

import android.view.MotionEvent
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AutocaptureMultiTouchGestureDetectorTest {
    @Test
    fun `tracks a pinch after scale passes the threshold`() {
        val gestures = mutableListOf<String>()
        val detector = AutocaptureMultiTouchGestureDetector { action, _, _ -> gestures += action }

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 130f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 130f, 0f))

        assertEquals(listOf("pinch"), gestures)
    }

    @Test
    fun `tracks a rotation after angle passes the threshold`() {
        val gestures = mutableListOf<String>()
        val detector = AutocaptureMultiTouchGestureDetector { action, _, _ -> gestures += action }

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 86.6f, 50f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 86.6f, 50f))

        assertEquals(listOf("rotation"), gestures)
    }

    @Test
    fun `does not track incidental two-finger movement`() {
        val gestures = mutableListOf<String>()
        val detector = AutocaptureMultiTouchGestureDetector { action, _, _ -> gestures += action }

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 105f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 105f, 0f))

        assertEquals(emptyList<String>(), gestures)
    }

    private fun event(
        action: Int,
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float,
    ): MotionEvent =
        mockk(relaxed = true) {
            every { actionMasked } returns action
            every { pointerCount } returns 2
            every { getX(0) } returns firstX
            every { getY(0) } returns firstY
            every { getX(1) } returns secondX
            every { getY(1) } returns secondY
        }
}
