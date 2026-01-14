package com.amplitude.android.internal.gestures

import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.amplitude.android.AutocaptureState
import com.amplitude.android.FrustrationInteractionsDetector
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.common.Logger

/**
 * Enhanced window callback that handles frustration interactions (e.g. rage click, dead click)
 */
internal class FrustrationAwareWindowCallback(
    delegate: Window.Callback,
    decorView: View,
    activityName: String,
    track: TrackFunction,
    viewTargetLocators: List<ViewTargetLocator>,
    logger: Logger,
    autocaptureState: AutocaptureState,
    private val frustrationDetector: FrustrationInteractionsDetector?,
) : AutocaptureWindowCallback(delegate, decorView, activityName, track, viewTargetLocators, logger, autocaptureState) {
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
        val decorView = decorViewRef.get()
        if (decorView == null) {
            logger.error("DecorView is null in handleFrustrationInteraction()")
            return
        }

        // Reuse the ViewTarget found by element interactions to avoid redundant view hierarchy traversal
        val target: ViewTarget =
            lastFoundViewTarget
                ?.also {
                    // Clear the cache after use to avoid stale references
                    lastFoundViewTarget = null
                } ?: decorView.findTarget(
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
            logger.debug("Ignoring all frustration interactions for target: ${target.className}")
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

        // Property building is now handled inside FrustrationInteractionsDetector
        // using EventPropertyUtils for consistent ELEMENT_INTERACTED -> RAGE/DEAD_CLICK hierarchy
        frustrationDetector?.processClick(clickInfo, targetInfo, target, activityName)
    }
}
