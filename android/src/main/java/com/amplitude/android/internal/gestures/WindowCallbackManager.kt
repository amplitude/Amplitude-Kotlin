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
import com.amplitude.common.Logger
import curtains.Curtains
import curtains.OnRootViewsChangedListener
import curtains.onDecorViewReady
import curtains.phoneWindow
import curtains.windowAttachCount

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
            if (view.windowAttachCount == 0) {
                // Window is being attached for the first time, wait for decor view to be ready
                window.onDecorViewReady { wrapWindow(window) }
            } else {
                // Window was previously attached, decor view should be available
                wrapWindow(window)
            }
        }
    }

    private fun onRootViewRemoved(view: View) {
        view.phoneWindow?.let { window ->
            unwrapWindow(window)
        }
    }

    private fun wrapWindow(window: Window) {
        // Avoid double-wrapping
        if (wrappedWindows.containsKey(window)) {
            return
        }

        // Try to get the Activity from the Window's context
        // Dialog windows often use ContextThemeWrapper, so we need to traverse the context chain
        val activity = window.context.findActivity()
        if (activity == null) {
            logger.debug("Unable to get Activity from window context, skipping window")
            return
        }

        val decorView = window.peekDecorView()
        if (decorView == null) {
            logger.debug("DecorView not ready for window, skipping")
            return
        }

        val originalCallback = window.callback ?: NoCaptureWindowCallback()

        // Store original callback before wrapping
        wrappedWindows[window] = window.callback

        val wrappedCallback =
            if (frustrationDetector != null) {
                FrustrationAwareWindowCallback(
                    delegate = originalCallback,
                    activity = activity,
                    decorView = decorView,
                    track = track,
                    viewTargetLocators = ALL(logger),
                    logger = logger,
                    autocaptureState = autocaptureState,
                    frustrationDetector = frustrationDetector,
                )
            } else {
                AutocaptureWindowCallback(
                    delegate = originalCallback,
                    activity = activity,
                    decorView = decorView,
                    track = track,
                    viewTargetLocators = ALL(logger),
                    logger = logger,
                    autocaptureState = autocaptureState,
                )
            }

        window.callback = wrappedCallback

        logger.debug("Wrapped window callback for ${activity.localClassName}")
    }

    private fun unwrapWindow(window: Window) {
        val originalCallback = wrappedWindows.remove(window) ?: return

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
    internal fun wrapWindowForTesting(window: Window) = wrapWindow(window)

    @VisibleForTesting
    internal fun unwrapWindowForTesting(window: Window) = unwrapWindow(window)
}
