package com.amplitude.android.internal.gestures

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.VisibleForTesting
import com.amplitude.android.AutocaptureState
import com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED
import com.amplitude.android.InteractionType.ElementInteraction
import com.amplitude.android.internal.ELEMENT_INTERACTED_ACTION_LONG_PRESS
import com.amplitude.android.internal.ELEMENT_INTERACTED_ACTION_TOUCH
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.buildElementInteractedProperties
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.common.Logger
import java.lang.ref.WeakReference

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class AutocaptureGestureListener(
    decorView: View,
    private val activityName: String,
    private val track: TrackFunction,
    private val logger: Logger,
    private val viewTargetLocators: List<ViewTargetLocator>,
    private val autocaptureStateProvider: () -> AutocaptureState,
    private var onViewTargetFound: ((ViewTarget) -> Unit)? = null,
) : GestureDetector.OnGestureListener {
    private val autocaptureState: AutocaptureState
        get() = autocaptureStateProvider()
    private val decorViewRef: WeakReference<View> = WeakReference(decorView)

    internal fun setViewTargetFoundCallback(callback: (ViewTarget) -> Unit) {
        onViewTargetFound = callback
    }

    override fun onDown(e: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val target = findClickableTarget(e, "onSingleTapUp") ?: return false

        // Notify callback with found target (for reuse by frustration interactions)
        onViewTargetFound?.invoke(target)
        trackElementInteracted(target, ELEMENT_INTERACTED_ACTION_TOUCH)
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        return false
    }

    override fun onLongPress(e: MotionEvent) {
        // Long-press is not a tap: do not cache the target for rage/dead click on ACTION_UP.
        if (ElementInteraction !in autocaptureState.interactions) return
        val target = findClickableTarget(e, "onLongPress") ?: return
        trackElementInteracted(target, ELEMENT_INTERACTED_ACTION_LONG_PRESS)
    }

    private fun findClickableTarget(
        event: MotionEvent,
        caller: String,
    ): ViewTarget? {
        // Short-circuit if no interactions are enabled — avoids expensive view hierarchy scan.
        if (autocaptureState.interactions.isEmpty()) return null

        val decorView =
            decorViewRef.get() ?: logger.error("DecorView is null in $caller()").let { return null }

        return decorView.findTarget(
            Pair(event.x, event.y),
            viewTargetLocators,
            ViewTarget.Type.Clickable,
            logger,
        ) ?: logger.warn("Unable to find click target. No event captured.").let {
            null
        }
    }

    private fun trackElementInteracted(
        target: ViewTarget,
        action: String,
    ) {
        if (ElementInteraction in autocaptureState.interactions) {
            val properties = buildElementInteractedProperties(target, activityName, action)
            track(ELEMENT_INTERACTED, properties)
        }
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        return false
    }
}
