package com.amplitude.android.internal.gestures

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Window
import com.amplitude.common.Logger

internal class AutocaptureWindowCallback(
    delegate: Window.Callback,
    activity: Activity,
    track: (String, Map<String, Any?>) -> Unit,
    private val logger: Logger,
) : WindowCallbackAdapter(delegate) {
    private val gestureListener = AutocaptureGestureListener(activity, track, logger)

    private val gestureDetector = GestureDetector(activity, gestureListener)

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            MotionEvent.obtain(event)?.let {
                try {
                    gestureDetector.onTouchEvent(event)
                } catch (e: Exception) {
                    logger.error("Error handling touch event: $e")
                } finally {
                    it.recycle()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
