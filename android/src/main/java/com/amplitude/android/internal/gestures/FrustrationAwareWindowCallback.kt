package com.amplitude.android.internal.gestures

import android.app.Activity
import android.view.MotionEvent
import android.view.Window
import com.amplitude.android.ExperimentalAmplitudeFeature
import com.amplitude.android.FrustrationInteractionsDetector
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.android.utilities.DefaultEventUtils.Companion.screenName
import com.amplitude.common.Logger
import com.amplitude.core.Constants.EventProperties.SCREEN_NAME

/**
 * Enhanced window callback that handles frustration interactions (e.g. rage click, dead click)
 */
@ExperimentalAmplitudeFeature
internal class FrustrationAwareWindowCallback(
    delegate: Window.Callback,
    activity: Activity,
    track: (String, Map<String, Any?>) -> Unit,
    viewTargetLocators: List<ViewTargetLocator>,
    logger: Logger,
    private val frustrationDetector: FrustrationInteractionsDetector?,
) : AutocaptureWindowCallback(delegate, activity, track, viewTargetLocators, logger) {
    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        // First handle the standard autocapture behavior
        val result = super.dispatchTouchEvent(event)

        // Then handle frustration interactions if detector is available
        event?.let { motionEvent ->
            if (frustrationDetector != null && motionEvent.action == MotionEvent.ACTION_UP) {
                handleFrustrationInteraction(motionEvent)
            }
        }

        return result
    }

    private fun handleFrustrationInteraction(event: MotionEvent) {
        val decorView = activity.window?.decorView
        if (decorView == null) {
            logger.error("DecorView is null in handleFrustrationInteraction()")
            return
        }

        val target: ViewTarget =
            decorView.findTarget(
                Pair(event.x, event.y),
                viewTargetLocators,
                ViewTarget.Type.Clickable,
                logger,
            ) ?: run {
                logger.debug("Unable to find click target for frustration interaction")
                return
            }

        // Check if this target should be ignored for all frustration analytics
        if (target.isIgnoredForFrustration) {
            logger.debug("Ignoring all frustration interactions for target with tag: ${target.tag}")
            return
        }

        // Convert Android-specific target to platform-agnostic format
        val clickInfo =
            FrustrationInteractionsDetector.ClickInfo(
                x = event.x,
                y = event.y,
            )

        val targetInfo =
            FrustrationInteractionsDetector.TargetInfo(
                className = target.className,
                resourceName = target.resourceName,
                tag = target.tag,
                text = target.text,
                source = target.source,
                hierarchy = target.hierarchy,
            )

        // Add Android-specific properties
        val additionalProperties =
            mapOf(
                SCREEN_NAME to activity.screenName,
            )

        frustrationDetector?.processClick(clickInfo, targetInfo, additionalProperties)
    }
}
