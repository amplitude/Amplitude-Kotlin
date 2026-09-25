package com.amplitude.android.internal.gestures

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.AbsListView
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.annotation.VisibleForTesting
import com.amplitude.android.AutocaptureState
import com.amplitude.android.Constants.EventTypes.ELEMENT_INTERACTED
import com.amplitude.android.InteractionType.ElementInteraction
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.buildElementInteractedProperties
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.common.Logger
import java.lang.ref.WeakReference

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
    private var panCandidate: Pair<Float, Float>? = null
    private var gestureTracked = false

    private val autocaptureState: AutocaptureState
        get() = autocaptureStateProvider()
    private val decorViewRef: WeakReference<View> = WeakReference(decorView)

    internal fun setViewTargetFoundCallback(callback: (ViewTarget) -> Unit) {
        onViewTargetFound = callback
    }

    override fun onDown(e: MotionEvent): Boolean {
        panCandidate = null
        gestureTracked = false
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
            trackInteraction(target, TAP)
        }

        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        if (ElementInteraction !in autocaptureState.interactions || gestureTracked) return false

        panCandidate = Pair(e2.x, e2.y)
        return false
    }

    override fun onLongPress(e: MotionEvent) {
        trackGesture(e, LONG_PRESS)
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        if (e1 != null) {
            trackGesture(e2, SWIPE)
        }
        return false
    }

    internal fun onTouchEventCompleted(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_UP && !gestureTracked) {
            panCandidate?.let { (x, y) -> trackGesture(x, y, PAN) }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            panCandidate = null
        }
    }

    internal fun onMultiTouchGesture(action: String, x: Float, y: Float) {
        if (gestureTracked) return
        trackGesture(x, y, action)
    }

    private fun trackGesture(
        event: MotionEvent,
        action: String,
    ) {
        trackGesture(event.x, event.y, action)
    }

    private fun trackGesture(
        x: Float,
        y: Float,
        action: String,
    ) {
        if (ElementInteraction !in autocaptureState.interactions || gestureTracked) return

        val decorView =
            decorViewRef.get()
                ?: logger.error("DecorView is null while tracking $action.").let { return }
        val target =
            decorView.findTarget(
                Pair(x, y),
                viewTargetLocators,
                ViewTarget.Type.Clickable,
                logger,
            ) ?: logger.warn("Unable to find $action target. No event captured.").let {
                return
            }

        if (action in SCROLL_GESTURES && target.isInScrollContainer()) return

        trackInteraction(target, action)
        gestureTracked = true
        panCandidate = null
    }

    private fun trackInteraction(
        target: ViewTarget,
        action: String,
    ) {
        val properties = buildElementInteractedProperties(target, activityName, action)
        track(ELEMENT_INTERACTED, properties)
    }

    private fun ViewTarget.isInScrollContainer(): Boolean {
        var current: Any? = view
        while (current is View) {
            if (current.isScrollContainerType()) return true
            current = current.parent
        }
        return false
    }

    private fun View.isScrollContainerType(): Boolean {
        return this is ScrollView ||
            this is HorizontalScrollView ||
            this is AbsListView ||
            this is WebView ||
            generateSequence(javaClass as Class<*>?) { it.superclass }
                .map { it.name }
                .any { it in ANDROIDX_SCROLL_CONTAINER_TYPES }
    }

    internal companion object {
        val TAP = "tap"
        val SWIPE = "swipe"
        val PAN = "pan"
        val LONG_PRESS = "longPress"
        val PINCH = "pinch"
        val ROTATION = "rotation"

        private val SCROLL_GESTURES = setOf(SWIPE, PAN, PINCH)
        private val ANDROIDX_SCROLL_CONTAINER_TYPES =
            setOf(
                "androidx.core.widget.NestedScrollView",
                "androidx.recyclerview.widget.RecyclerView",
                "androidx.viewpager.widget.ViewPager",
                "androidx.viewpager2.widget.ViewPager2",
            )
    }
}
