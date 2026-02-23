package com.amplitude.android.internal.gestures

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.amplitude.android.AutocaptureState
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.common.Logger
import java.lang.ref.WeakReference

internal open class AutocaptureWindowCallback(
    delegate: Window.Callback,
    decorView: View,
    protected val activityName: String,
    track: TrackFunction,
    protected val viewTargetLocators: List<ViewTargetLocator>,
    protected val logger: Logger,
    autocaptureStateProvider: () -> AutocaptureState,
    private val motionEventObtainer: MotionEventObtainer = object : MotionEventObtainer {},
    protected val gestureListener: AutocaptureGestureListener =
        AutocaptureGestureListener(decorView, activityName, track, logger, viewTargetLocators, autocaptureStateProvider),
    private val gestureDetector: GestureDetector = GestureDetector(decorView.context, gestureListener),
) : WindowCallbackAdapter(delegate) {
    protected val decorViewRef: WeakReference<View> = WeakReference(decorView)

    /**
     * The last ViewTarget found during tap processing.
     * This is used to avoid redundant view hierarchy traversal when both
     * element interactions and frustration interactions are enabled.
     */
    protected var lastFoundViewTarget: ViewTarget? = null

    init {
        // Set up the callback to cache ViewTarget for frustration interactions
        gestureListener.setViewTargetFoundCallback { lastFoundViewTarget = it }
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            motionEventObtainer.obtain(event).let {
                try {
                    gestureDetector.onTouchEvent(it)
                } catch (e: Exception) {
                    logger.error("Error handling touch event: $e")
                } finally {
                    it.recycle()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    interface MotionEventObtainer {
        fun obtain(origin: MotionEvent): MotionEvent {
            return MotionEvent.obtain(origin)
        }
    }
}
