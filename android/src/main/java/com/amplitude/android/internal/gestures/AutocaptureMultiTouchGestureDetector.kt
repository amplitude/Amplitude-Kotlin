package com.amplitude.android.internal.gestures

import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

internal class AutocaptureMultiTouchGestureDetector(
    private val onTrackingStarted: (x: Float, y: Float) -> Unit,
    private val onGesture: (action: String) -> Unit,
    private val onTrackingEnded: () -> Unit = {},
) {
    private var firstPointerId = MotionEvent.INVALID_POINTER_ID
    private var secondPointerId = MotionEvent.INVALID_POINTER_ID
    private var initialSpan = 0f
    private var initialAngle = 0f
    private var scaleDelta = 0f
    private var rotationDelta = 0f
    private var tracking = false

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> reset()
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!tracking && event.pointerCount == REQUIRED_POINTER_COUNT) {
                    firstPointerId = event.getPointerId(0)
                    secondPointerId = event.getPointerId(1)
                    initialSpan = event.span(0, 1)
                    initialAngle = event.angle(0, 1)
                    tracking = true
                    // Report the focus now, before the gesture moves any content.
                    onTrackingStarted((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking) {
                    val firstIndex = event.findPointerIndex(firstPointerId)
                    val secondIndex = event.findPointerIndex(secondPointerId)
                    if (firstIndex < 0 || secondIndex < 0) {
                        reset()
                    } else if (initialSpan > 0f) {
                        scaleDelta = abs(event.span(firstIndex, secondIndex) / initialSpan - 1f)
                        rotationDelta = abs(normalizeAngle(event.angle(firstIndex, secondIndex) - initialAngle))
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId == firstPointerId || pointerId == secondPointerId) {
                    finishTracking()
                }
            }
            MotionEvent.ACTION_UP -> finishTracking()
            MotionEvent.ACTION_CANCEL -> reset()
        }
    }

    private fun finishTracking() {
        if (!tracking) return
        val action =
            when {
                rotationDelta >= ROTATION_THRESHOLD &&
                    rotationDelta / ROTATION_THRESHOLD > scaleDelta / SCALE_THRESHOLD -> GestureActions.ROTATION
                scaleDelta >= SCALE_THRESHOLD -> GestureActions.PINCH
                rotationDelta >= ROTATION_THRESHOLD -> GestureActions.ROTATION
                else -> null
            }
        action?.let { onGesture(it) }
        reset()
    }

    private fun MotionEvent.span(
        firstIndex: Int,
        secondIndex: Int,
    ): Float = hypot(getX(secondIndex) - getX(firstIndex), getY(secondIndex) - getY(firstIndex))

    private fun MotionEvent.angle(
        firstIndex: Int,
        secondIndex: Int,
    ): Float =
        Math.toDegrees(
            atan2(
                (getY(secondIndex) - getY(firstIndex)).toDouble(),
                (getX(secondIndex) - getX(firstIndex)).toDouble(),
            ),
        ).toFloat()

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > HALF_ROTATION) normalized -= FULL_ROTATION
        while (normalized < -HALF_ROTATION) normalized += FULL_ROTATION
        return normalized
    }

    private fun reset() {
        val wasTracking = tracking
        firstPointerId = MotionEvent.INVALID_POINTER_ID
        secondPointerId = MotionEvent.INVALID_POINTER_ID
        initialSpan = 0f
        initialAngle = 0f
        scaleDelta = 0f
        rotationDelta = 0f
        tracking = false
        if (wasTracking) onTrackingEnded()
    }

    private companion object {
        const val REQUIRED_POINTER_COUNT = 2
        const val SCALE_THRESHOLD = 0.1f
        const val ROTATION_THRESHOLD = 15f
        const val HALF_ROTATION = 180f
        const val FULL_ROTATION = 360f
    }
}
