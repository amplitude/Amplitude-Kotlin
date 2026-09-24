package com.amplitude.android.internal.gestures

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.VisibleForTesting
import com.amplitude.android.AutocaptureState
import com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED
import com.amplitude.android.InteractionType.ElementInteraction
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.buildElementInteractedProperties
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.android.internal.resolvedFor
import com.amplitude.android.internal.tapAction
import com.amplitude.android.internal.resolvedAs
import com.amplitude.common.Logger
import java.lang.ref.WeakReference
import kotlin.math.abs

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
@Deprecated("Not intended for public use. Will be internal in a future release.")
public class AutocaptureGestureListener(
    decorView: View,
    private val activityName: String,
    private val track: TrackFunction,
    private val logger: Logger,
    private val viewTargetLocators: List<ViewTargetLocator>,
    private val autocaptureStateProvider: () -> AutocaptureState,
    private var onViewTargetFound: ((ViewTarget) -> Unit)? = null,
) : GestureDetector.OnGestureListener {
    private var panStarted = false
    private var panTarget: ViewTarget? = null
    private var dragAction: String = GestureActions.PAN
    private var transformHit: ViewTarget? = null
    private var gestureTracked = false

    private val autocaptureState: AutocaptureState
        get() = autocaptureStateProvider()
    private val decorViewRef: WeakReference<View> = WeakReference(decorView)

    internal fun setViewTargetFoundCallback(callback: (ViewTarget) -> Unit) {
        onViewTargetFound = callback
    }

    override fun onDown(e: MotionEvent): Boolean {
        panStarted = false
        panTarget = null
        dragAction = GestureActions.PAN
        transformHit = null
        gestureTracked = false
        return false
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // Short-circuit if no interactions are enabled — avoids expensive view hierarchy scan.
        if (autocaptureState.interactions.isEmpty()) return false

        val decorView = decorViewRef.get() ?: logger.error("DecorView is null in onSingleTapUp()").let { return false }

        val hit =
            decorView.findTarget(
                Pair(e.x, e.y),
                viewTargetLocators,
                ViewTarget.Type.Clickable,
                logger,
            ) ?: logger.warn("Unable to find click target. No event captured.").let {
                return false
            }
        val action = hit.tapAction() ?: return false
        val target = hit.resolvedFor(action) ?: return false

        // Notify callback with found target (for reuse by frustration interactions)
        onViewTargetFound?.invoke(target)

        // Track element interaction events only if ElementInteraction is enabled
        if (ElementInteraction in autocaptureState.interactions) {
            trackInteraction(target, target.interactionAction.eventValue)
        }

        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        if (panStarted || e1 == null) return false
        panStarted = true

        // Resolve before this drag moves any content, so it belongs to the element it started
        // on. The innermost owner wins: a slider claims pan, and a switch claims a horizontal
        // drag as a value change. A vertical drag on a switch is a scroll, so it falls through
        // to an ancestor that pans, or to nobody.
        val hit = findHit(e1.x, e1.y) ?: return false
        val horizontal = abs(distanceX) > abs(distanceY)
        for (owner in hit.gestureOwners.asReversed()) {
            when {
                GestureActions.PAN in owner.actions -> {
                    panTarget = hit.resolvedAs(owner)
                    dragAction = GestureActions.PAN
                    return false
                }
                owner.dragReportsValueChange && horizontal -> {
                    panTarget = hit.resolvedAs(owner)
                    dragAction = GestureActions.VALUE_CHANGE
                    return false
                }
            }
        }
        return false
    }

    override fun onLongPress(e: MotionEvent) {
        trackGesture(e.x, e.y, GestureActions.LONG_PRESS)
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean = false

    internal fun onTouchEventCompleted(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            panTarget?.let { trackGesture(it, dragAction) }
        }
        // The transform detector reports a qualified gesture before this runs, so dropping the
        // captured target here only discards one that never crossed its threshold.
        if (event.actionMasked == MotionEvent.ACTION_POINTER_UP ||
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            transformHit = null
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            panStarted = false
            panTarget = null
            dragAction = GestureActions.PAN
        }
    }

    /**
     * Called when a second finger lands, before the gesture has moved anything.
     */
    internal fun onTransformStarted(x: Float, y: Float) {
        transformHit = findHit(x, y)
    }

    /**
     * Called when a pinch or rotation crosses its threshold. The target is the one captured in
     * [onTransformStarted], not whatever is under the fingers now.
     */
    internal fun onTransformRecognized(action: String) {
        val hit = transformHit
        transformHit = null
        hit?.resolvedFor(action)?.let { trackGesture(it, action) }
    }

    private fun trackGesture(
        x: Float,
        y: Float,
        action: String,
    ) {
        findGestureTarget(x, y, action)?.let { trackGesture(it, action) }
    }

    private fun trackGesture(
        target: ViewTarget,
        action: String,
    ) {
        if (gestureTracked) return
        trackInteraction(target, action)
        gestureTracked = true
    }

    private fun findGestureTarget(
        x: Float,
        y: Float,
        action: String,
    ): ViewTarget? = findHit(x, y)?.resolvedFor(action)

    /**
     * The element under [x], [y]. A miss is normal: most pans and pinches start on content that
     * handles neither, so this stays quiet where a tap logs that it found nothing.
     */
    private fun findHit(
        x: Float,
        y: Float,
    ): ViewTarget? {
        if (ElementInteraction !in autocaptureState.interactions || gestureTracked) return null

        val decorView =
            decorViewRef.get()
                ?: logger.error("DecorView is null while resolving a gesture target.").let { return null }
        return decorView.findTarget(
            Pair(x, y),
            viewTargetLocators,
            ViewTarget.Type.Clickable,
            logger,
        )
    }

    private fun trackInteraction(
        target: ViewTarget,
        action: String,
    ) {
        val properties = buildElementInteractedProperties(target, activityName, action)
        track(ELEMENT_INTERACTED, properties)
    }
}
