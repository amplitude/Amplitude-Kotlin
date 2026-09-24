package com.amplitude.android.internal.gestures

import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

internal class AutocaptureMultiTouchGestureDetector(
    private val onGesture: (action: String, x: Float, y: Float) -> Unit,
) {
    private var initialSpan = 0f
    private var initialAngle = 0f
    private var scaleDelta = 0f
    private var rotationDelta = 0f
    private var focusX = 0f
    private var focusY = 0f
    private var tracking = false

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> reset()
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == REQUIRED_POINTER_COUNT) {
                    initialSpan = event.span()
                    initialAngle = event.angle()
                    updateFocus(event)
                    tracking = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (tracking && initialSpan > 0f && event.pointerCount >= REQUIRED_POINTER_COUNT) {
                    scaleDelta = abs(event.span() / initialSpan - 1f)
                    rotationDelta = abs(normalizeAngle(event.angle() - initialAngle))
                    updateFocus(event)
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                if (tracking) {
                    val action =
                        when {
                            rotationDelta >= ROTATION_THRESHOLD &&
                                rotationDelta / ROTATION_THRESHOLD > scaleDelta / SCALE_THRESHOLD -> {
                                AutocaptureGestureListener.ROTATION
                            }
                            scaleDelta >= SCALE_THRESHOLD -> AutocaptureGestureListener.PINCH
                            rotationDelta >= ROTATION_THRESHOLD -> AutocaptureGestureListener.ROTATION
                            else -> null
                        }
                    action?.let { onGesture(it, focusX, focusY) }
                    reset()
                }
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
    }

    private fun MotionEvent.span(): Float = hypot(getX(1) - getX(0), getY(1) - getY(0))

    private fun MotionEvent.angle(): Float =
        Math.toDegrees(
            atan2(
                (getY(1) - getY(0)).toDouble(),
                (getX(1) - getX(0)).toDouble(),
            ),
        ).toFloat()

    private fun updateFocus(event: MotionEvent) {
        focusX = (event.getX(0) + event.getX(1)) / 2
        focusY = (event.getY(0) + event.getY(1)) / 2
    }

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        while (normalized > HALF_ROTATION) normalized -= FULL_ROTATION
        while (normalized < -HALF_ROTATION) normalized += FULL_ROTATION
        return normalized
    }

    private fun reset() {
        initialSpan = 0f
        initialAngle = 0f
        scaleDelta = 0f
        rotationDelta = 0f
        focusX = 0f
        focusY = 0f
        tracking = false
    }

    private companion object {
        const val REQUIRED_POINTER_COUNT = 2
        const val SCALE_THRESHOLD = 0.1f
        const val ROTATION_THRESHOLD = 15f
        const val HALF_ROTATION = 180f
        const val FULL_ROTATION = 360f
    }
}
