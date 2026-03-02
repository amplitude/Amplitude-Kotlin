package com.amplitude.android.internal.gestures

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.VisibleForTesting
import com.amplitude.android.AutocaptureState
import com.amplitude.android.InteractionType.ElementInteraction
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.buildElementInteractedProperties
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.common.Logger
import com.amplitude.core.Constants.EventTypes.ELEMENT_INTERACTED
import java.lang.ref.WeakReference

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class AutocaptureGestureListener(
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
        // Short-circuit if no interactions are enabled — avoids expensive view hierarchy scan.
        if (autocaptureState.interactions.isEmpty()) return false

        val decorView = decorViewRef.get() ?: logger.error("DecorView is null in onSingleTapUp()").let { return false }

        val target: ViewTarget =
            decorView.findTarget(
                Pair(e.x, e.y),
                viewTargetLocators,
                ViewTarget.Type.Clickable,
                logger,
            ) ?: logger.warn("Unable to find click target. No event captured.").let {
                return false
            }

        // Notify callback with found target (for reuse by frustration interactions)
        onViewTargetFound?.invoke(target)

        // Track element interaction events only if ElementInteraction is enabled
        if (ElementInteraction in autocaptureState.interactions) {
            // Build ELEMENT_INTERACTED properties using shared function
            val properties = buildElementInteractedProperties(target, activityName)
            track(ELEMENT_INTERACTED, properties)
        }

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

    override fun onLongPress(e: MotionEvent) {}

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        return false
    }
}
