package com.amplitude.android.internal.gestures

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Window
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.common.Logger

internal class AutocaptureWindowCallback(
    delegate: Window.Callback,
    activity: Activity,
    track: (String, Map<String, Any?>) -> Unit,
    viewTargetLocators: List<ViewTargetLocator>,
    private val logger: Logger,
    private val motionEventObtainer: MotionEventObtainer = object : MotionEventObtainer {},
    private val gestureListener: AutocaptureGestureListener =
        AutocaptureGestureListener(activity, track, logger, viewTargetLocators),
    private val gestureDetector: GestureDetector = GestureDetector(activity, gestureListener),
) : WindowCallbackAdapter(delegate) {
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            motionEventObtainer.obtain(event).let {
                try {
                    handleTouchEvent(it)
                } catch (e: Exception) {
                    logger.error("Error handling touch event: $e")
                } finally {
                    it.recycle()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun handleTouchEvent(event: MotionEvent) {
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            gestureListener.onUp(event)
        }
    }

    interface MotionEventObtainer {
        fun obtain(origin: MotionEvent): MotionEvent {
            return MotionEvent.obtain(origin)
        }
    }
}
