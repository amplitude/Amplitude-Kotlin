package com.amplitude.android.internal.gestures

import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.amplitude.android.AutocaptureState
import com.amplitude.android.FrustrationInteractionsDetector
import com.amplitude.android.InteractionType.DeadClick
import com.amplitude.android.InteractionType.RageClick
import com.amplitude.android.internal.TrackFunction
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
    private val autocaptureStateProvider: () -> AutocaptureState,
    private val frustrationDetector: FrustrationInteractionsDetector?,
) : AutocaptureWindowCallback(delegate, decorView, activityName, track, viewTargetLocators, logger, autocaptureStateProvider) {
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
        // Short-circuit if no frustration interactions are enabled.
        val state = autocaptureStateProvider()
        if (RageClick !in state.interactions && DeadClick !in state.interactions) {
            // Clear any cached target from element interactions so it is not reused
            // if frustration tracking is enabled again later.
            lastFoundViewTarget = null
            return
        }

        // Only a completed tap caches a target, so drag and long-press releases are not clicks
        val target =
            lastFoundViewTarget ?: run {
                logger.debug("No tap target for frustration interaction")
                return
            }
        // Clear the cache after use to avoid stale references
        lastFoundViewTarget = null

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
