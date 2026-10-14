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
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 130f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 130f, 0f))

        assertEquals(listOf("pinch"), gestures)
    }

    @Test
    fun `tracks a rotation after angle passes the threshold`() {
        val gestures = mutableListOf<String>()
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 86.6f, 50f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 86.6f, 50f))

        assertEquals(listOf("rotation"), gestures)
    }

    @Test
    fun `tracks a pinch that crossed the threshold and settled back`() {
        val gestures = mutableListOf<String>()
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 130f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 105f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 105f, 0f))

        assertEquals(listOf(GestureActions.PINCH), gestures)
    }

    @Test
    fun `tracks a rotation that crossed the threshold and settled back`() {
        val gestures = mutableListOf<String>()
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 86.6f, 50f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 100f, 5f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 100f, 5f))

        assertEquals(listOf(GestureActions.ROTATION), gestures)
    }

    @Test
    fun `does not track incidental two-finger movement`() {
        val gestures = mutableListOf<String>()
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 105f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 105f, 0f))

        assertEquals(emptyList<String>(), gestures)
    }

    @Test
    fun `reports the focus where the second finger went down`() {
        val starts = mutableListOf<Pair<Float, Float>>()
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { x, y -> starts += x to y },
                onGesture = {},
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 40f, 10f, 140f, 10f))

        assertEquals(listOf(50f to 0f), starts)
    }

    @Test
    fun `continues a pinch when an extra finger lifts before the threshold`() {
        val gestures = mutableListOf<String>()
        var ends = 0
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
                onTrackingEnded = { ends++ },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 105f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 105f, 0f, pointerIds = listOf(0, 1, 2)))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 105f, 0f, pointerIds = listOf(0, 1, 2)))

        assertEquals(emptyList<String>(), gestures)
        assertEquals(0, ends)
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 130f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 130f, 0f))

        assertEquals(listOf(GestureActions.PINCH), gestures)
        assertEquals(1, ends)
    }

    @Test
    fun `does not finish a qualified transform when an extra finger lifts`() {
        val gestures = mutableListOf<String>()
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 130f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 130f, 0f, pointerIds = listOf(0, 1, 2)))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 130f, 0f, pointerIds = listOf(0, 1, 2)))

        assertEquals(emptyList<String>(), gestures)
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 65f, 112.6f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 65f, 112.6f))

        assertEquals(listOf(GestureActions.ROTATION), gestures)
    }

    @Test
    fun `follows the original pointer IDs when their indices change`() {
        val gestures = mutableListOf<String>()
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f, pointerIds = listOf(7, 12)))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f, pointerIds = listOf(7, 12, 19)))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 130f, 0f, 0f, 0f, pointerIds = listOf(12, 7, 19)))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 130f, 0f, 0f, 0f, pointerIds = listOf(12, 7, 19)))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 130f, 0f, 0f, 0f, pointerIds = listOf(12, 7)))

        assertEquals(listOf(GestureActions.PINCH), gestures)
    }

    @Test
    fun `finishes once when a tracked finger lifts while an extra finger remains`() {
        val gestures = mutableListOf<String>()
        var ends = 0
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
                onTrackingEnded = { ends++ },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f, pointerIds = listOf(0, 1, 2)))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 130f, 0f, pointerIds = listOf(0, 1, 2)))
        detector.onTouchEvent(
            event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 130f, 0f, pointerIds = listOf(0, 1, 2), liftedIndex = 0),
        )
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 200f, 0f, pointerIds = listOf(1, 2)))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 200f, 0f, pointerIds = listOf(1, 2)))

        assertEquals(listOf(GestureActions.PINCH), gestures)
        assertEquals(1, ends)
    }

    @Test
    fun `ends tracking without reporting an unqualified or cancelled transform`() {
        val gestures = mutableListOf<String>()
        var ends = 0
        val detector =
            AutocaptureMultiTouchGestureDetector(
                onTrackingStarted = { _, _ -> },
                onGesture = { gestures += it },
                onTrackingEnded = { ends++ },
            )

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP, 0f, 0f, 105f, 0f))
        assertEquals(1, ends)

        detector.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN, 0f, 0f, 100f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, 0f, 130f, 0f))
        detector.onTouchEvent(event(MotionEvent.ACTION_CANCEL, 0f, 0f, 130f, 0f))

        assertEquals(emptyList<String>(), gestures)
        assertEquals(2, ends)
    }

    private fun event(
        action: Int,
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float,
        pointerIds: List<Int> = listOf(0, 1),
        liftedIndex: Int = pointerIds.lastIndex,
    ): MotionEvent =
        mockk(relaxed = true) {
            every { actionMasked } returns action
            every { actionIndex } returns liftedIndex
            every { pointerCount } returns pointerIds.size
            every { getPointerId(any()) } answers { pointerIds[firstArg<Int>()] }
            every { findPointerIndex(any()) } answers { pointerIds.indexOf(firstArg<Int>()) }
            every { getX(0) } returns firstX
            every { getY(0) } returns firstY
            every { getX(1) } returns secondX
            every { getY(1) } returns secondY
            every { getX(2) } returns 500f
            every { getY(2) } returns 500f
        }
}
