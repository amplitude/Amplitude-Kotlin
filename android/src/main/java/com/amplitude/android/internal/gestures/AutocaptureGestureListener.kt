package com.amplitude.android.internal.gestures

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.annotation.VisibleForTesting
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.android.utilities.DefaultEventUtils.Companion.screenName
import com.amplitude.common.Logger
import com.amplitude.core.Constants.EventProperties.ACTION
import com.amplitude.core.Constants.EventProperties.HIERARCHY
import com.amplitude.core.Constants.EventProperties.SCREEN_NAME
import com.amplitude.core.Constants.EventProperties.TARGET_CLASS
import com.amplitude.core.Constants.EventProperties.TARGET_RESOURCE
import com.amplitude.core.Constants.EventProperties.TARGET_SOURCE
import com.amplitude.core.Constants.EventProperties.TARGET_TAG
import com.amplitude.core.Constants.EventProperties.TARGET_TEXT
import com.amplitude.core.Constants.EventTypes.ELEMENT_INTERACTED
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
            ACTION to "touch",
            TARGET_CLASS to target.className,
            TARGET_RESOURCE to target.resourceName,
            TARGET_TAG to target.tag,
            TARGET_TEXT to target.text,
            TARGET_SOURCE to
                target.source
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
            HIERARCHY to target.hierarchy,
            SCREEN_NAME to
                try {
                    activityRef.get()?.screenName
                } catch (e: Exception) {
                    logger.error("Error getting screen name: $e")
                    null
                },
        ).let { track(ELEMENT_INTERACTED, it) }

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
