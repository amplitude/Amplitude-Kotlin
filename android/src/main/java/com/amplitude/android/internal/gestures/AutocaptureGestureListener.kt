package com.amplitude.android.internal.gestures

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.annotation.VisibleForTesting
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_CLASS
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_RESOURCE
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_SOURCE
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_TAG
import com.amplitude.android.utilities.DefaultEventUtils.EventTypes.ELEMENT_CLICKED
import com.amplitude.common.Logger
import java.lang.ref.WeakReference

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class AutocaptureGestureListener(
    activity: Activity,
    private val track: (String, Map<String, Any?>) -> Unit,
    private val logger: Logger,
    private val viewTargetLocators: List<ViewTargetLocator>,
) : GestureDetector.OnGestureListener {
    private val activityRef: WeakReference<Activity> = WeakReference(activity)

    override fun onDown(e: MotionEvent): Boolean {
        return false
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        val decorView =
            activityRef.get()?.window?.decorView
                ?: logger.error("DecorView is null in onSingleTapUp()").let { return false }
        val target: ViewTarget =
            decorView.findTarget(
                Pair(e.x, e.y),
                viewTargetLocators,
                ViewTarget.Type.Clickable,
                logger,
            ) ?: logger.warn("Unable to find click target. No event captured.").let { return false }

        mapOf(
            ELEMENT_CLASS to target.className,
            ELEMENT_RESOURCE to target.resourceName,
            ELEMENT_TAG to target.tag,
            ELEMENT_SOURCE to
                target.source
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
        ).let { track(ELEMENT_CLICKED, it) }

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
