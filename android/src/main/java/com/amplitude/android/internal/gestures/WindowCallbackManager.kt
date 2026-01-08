package com.amplitude.android.internal.gestures

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.annotation.VisibleForTesting
import com.amplitude.android.AutocaptureState
import com.amplitude.android.FrustrationInteractionsDetector
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.locators.ViewTargetLocators.ALL
import com.amplitude.android.utilities.DefaultEventUtils.Companion.screenName
import com.amplitude.common.Logger
import curtains.Curtains
import curtains.OnRootViewsChangedListener
import curtains.onDecorViewReady
import curtains.phoneWindow

/**
 * Manages window callback wrapping for all windows (including dialogs) using Curtains lib.
 *
 * This class ensures element interactions are tracked inside dialogs
 * (including NavGraph dialogs) by monitoring all windows in the application and wrapping
 * each window's callback.
 */
internal class WindowCallbackManager(
    private val track: TrackFunction,
    private val frustrationDetector: FrustrationInteractionsDetector?,
    private val autocaptureState: AutocaptureState,
    private val logger: Logger,
) {
    private val wrappedWindows = mutableMapOf<Window, Window.Callback?>()

    private val rootViewsChangedListener =
        OnRootViewsChangedListener { view, added ->
            if (added) {
                onRootViewAdded(view)
            } else {
                onRootViewRemoved(view)
            }
        }

    fun start() {
        Curtains.onRootViewsChangedListeners += rootViewsChangedListener
        // Wrap any existing windows
        Curtains.rootViews.forEach(::onRootViewAdded)
    }

    fun stop() {
        Curtains.onRootViewsChangedListeners -= rootViewsChangedListener
        // Unwrap all windows
        wrappedWindows.keys.toList().forEach(::unwrapWindow)
    }

    private fun onRootViewAdded(view: View) {
        view.phoneWindow?.let { window ->
            window.onDecorViewReady { decorView -> wrapWindow(window, decorView) }
        }
    }

    private fun onRootViewRemoved(view: View) {
        view.phoneWindow?.let { window ->
            unwrapWindow(window)
        }
    }

    private fun wrapWindow(
        window: Window,
        decorView: View,
    ) {
        // Avoid double-wrapping
        if (wrappedWindows.containsKey(window)) {
            return
        }

        // Try to get the Activity from the Window's context to extract the screen name
        // Dialog windows often use ContextThemeWrapper, so we need to traverse the context chain
        val activityName =
            window.context
                .findActivity()
                ?.screenName
                ?: run {
                    logger.debug("Unable to get Activity from window context, skipping window")
                    return
                }

        val originalCallback = window.callback ?: NoCaptureWindowCallback()

        // Store original callback before wrapping
        wrappedWindows[window] = window.callback

        val wrappedCallback =
            if (frustrationDetector != null) {
                FrustrationAwareWindowCallback(
                    delegate = originalCallback,
                    decorView = decorView,
                    activityName = activityName,
                    track = track,
                    viewTargetLocators = ALL(logger),
                    logger = logger,
                    autocaptureState = autocaptureState,
                    frustrationDetector = frustrationDetector,
                )
            } else {
                AutocaptureWindowCallback(
                    delegate = originalCallback,
                    decorView = decorView,
                    activityName = activityName,
                    track = track,
                    viewTargetLocators = ALL(logger),
                    logger = logger,
                    autocaptureState = autocaptureState,
                )
            }

        window.callback = wrappedCallback

        logger.debug("Wrapped window callback for $activityName")
    }

    private fun unwrapWindow(window: Window) {
        if (!wrappedWindows.containsKey(window)) return
        val originalCallback = wrappedWindows.remove(window)

        // Only unwrap if the current callback is our wrapper
        val currentCallback = window.callback
        if (currentCallback is AutocaptureWindowCallback) {
            // Restore original callback, or null if it was NoCaptureWindowCallback
            window.callback = originalCallback.takeUnless { it is NoCaptureWindowCallback }
            logger.debug("Unwrapped window callback")
        }
    }

    /**
     * Traverses the context chain to find the Activity.
     * This is necessary because dialogs often wrap the Activity context
     * with ContextThemeWrapper or other wrappers.
     */
    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    @VisibleForTesting
    internal fun wrapWindowForTesting(
        window: Window,
        decorView: View,
    ) = wrapWindow(window, decorView)

    @VisibleForTesting
    internal fun unwrapWindowForTesting(window: Window) = unwrapWindow(window)
}
