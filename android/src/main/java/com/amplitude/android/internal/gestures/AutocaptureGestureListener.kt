package com.amplitude.android.internal.gestures

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.annotation.VisibleForTesting
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.DIRECTION
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_CLASS
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_RESOURCE
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_SOURCE
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_TAG
import com.amplitude.android.utilities.DefaultEventUtils.EventTypes.ELEMENT_CLICKED
import com.amplitude.android.utilities.DefaultEventUtils.EventTypes.ELEMENT_SCROLLED
import com.amplitude.android.utilities.DefaultEventUtils.EventTypes.ELEMENT_SWIPED
import com.amplitude.common.Logger
import java.lang.ref.WeakReference
import kotlin.math.abs

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class AutocaptureGestureListener(
    activity: Activity,
    private val track: (String, Map<String, Any?>) -> Unit,
    private val logger: Logger,
    private val viewTargetLocators: List<ViewTargetLocator>,
) : GestureDetector.OnGestureListener {
    private enum class MoveGestureType {
        Scroll,
        Swipe,
        Unknown,
    }

    private val activityRef: WeakReference<Activity> = WeakReference(activity)
    private val scrollState = ScrollState()

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val decorView = ensureWindowDecorView("onSingleTapUp") ?: return false

        val target: ViewTarget =
            decorView.findTarget(
                Pair(e.x, e.y),
                viewTargetLocators,
                ViewTarget.Type.Clickable,
                logger,
            ) ?: logger.warn("Unable to find click target. No event captured.").let { return false }

        capture(target, ELEMENT_CLICKED)
        return false
    }

    override fun onDown(e: MotionEvent): Boolean {
        scrollState.reset()
        scrollState.startX = e.x
        scrollState.startY = e.y
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
    ): Boolean {
        val decorView = ensureWindowDecorView("onScroll") ?: return false
        if (e1 == null || scrollState.type != MoveGestureType.Unknown) {
            return false
        }

        val target: ViewTarget =
            decorView.findTarget(
                Pair(e1.x, e1.y),
                viewTargetLocators,
                ViewTarget.Type.Scrollable,
                logger,
            ) ?: logger.warn("Unable to find scroll target. No event captured.").let { return false }

        scrollState.setTarget(target)
        scrollState.type = MoveGestureType.Scroll
        return false
    }

    fun onUp(e: MotionEvent) {
        ensureWindowDecorView("onUp") ?: return

        val scrollTarget = scrollState.viewTarget ?: return
        if (scrollState.type == MoveGestureType.Unknown) {
            logger.warn("Unable to define scroll type. No event captured.")
            return
        }

        val direction = scrollState.calculateDirection(e)
        val eventType =
            if (scrollState.type == MoveGestureType.Scroll) {
                ELEMENT_SCROLLED
            } else {
                ELEMENT_SWIPED
            }

        capture(scrollTarget, eventType, mapOf(DIRECTION to direction))
        scrollState.reset()
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        scrollState.type = MoveGestureType.Swipe
        return false
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onLongPress(e: MotionEvent) {}

    private fun ensureWindowDecorView(caller: String): View? {
        val activity = activityRef.get()
        val window = activity?.window
        val decorView = window?.decorView

        if (activity == null) {
            logger.error("Activity is null in $caller")
        } else if (window == null) {
            logger.error("Window is null in $caller")
        } else if (decorView == null) {
            logger.error("DecorView is null in $caller")
        }

        return decorView
    }

    private fun capture(
        target: ViewTarget,
        eventType: String,
        additionalProperties: Map<String, Any?> = emptyMap(),
    ) {
        mapOf(
            ELEMENT_CLASS to target.className,
            ELEMENT_RESOURCE to target.recourseName,
            ELEMENT_TAG to target.tag,
            ELEMENT_SOURCE to
                target.source
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
        ).let { track(eventType, it + additionalProperties) }
    }

    private inner class ScrollState {
        var type = MoveGestureType.Unknown
        var viewTarget: ViewTarget? = null
        var startX = 0f
        var startY = 0f

        fun setTarget(target: ViewTarget) {
            this.viewTarget = target
        }

        /**
         * Calculates the direction of the scroll/swipe based on startX and startY and a given event
         *
         * @param endEvent - the event which notifies when the scroll/swipe ended
         * @return String, one of (left|right|up|down)
         */
        fun calculateDirection(endEvent: MotionEvent): String {
            val diffX = endEvent.x - startX
            val diffY = endEvent.y - startY
            val direction =
                if (abs(diffX.toDouble()) > abs(diffY.toDouble())) {
                    if (diffX > 0f) {
                        "Right"
                    } else {
                        "Left"
                    }
                } else {
                    if (diffY > 0) {
                        "Down"
                    } else {
                        "Up"
                    }
                }
            return direction
        }

        fun reset() {
            viewTarget = null
            type = MoveGestureType.Unknown
            startX = 0f
            startY = 0f
        }
    }
}
