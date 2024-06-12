package com.amplitude.android.internal.gestures

import android.view.ActionMode
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.SearchEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

internal class NoCaptureWindowCallback : Window.Callback {
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        return false
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent?): Boolean {
        return false
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        return false
    }

    override fun dispatchTrackballEvent(event: MotionEvent?): Boolean {
        return false
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        return false
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean {
        return false
    }

    override fun onCreatePanelView(featureId: Int): View? {
        return null
    }

    override fun onCreatePanelMenu(
        featureId: Int,
        menu: Menu,
    ): Boolean {
        return false
    }

    override fun onPreparePanel(
        featureId: Int,
        view: View?,
        menu: Menu,
    ): Boolean {
        return false
    }

    override fun onMenuOpened(
        featureId: Int,
        menu: Menu,
    ): Boolean {
        return false
    }

    override fun onMenuItemSelected(
        featureId: Int,
        item: MenuItem,
    ): Boolean {
        return false
    }

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {}

    override fun onContentChanged() {}

    override fun onWindowFocusChanged(hasFocus: Boolean) {}

    override fun onAttachedToWindow() {}

    override fun onDetachedFromWindow() {}

    override fun onPanelClosed(
        featureId: Int,
        menu: Menu,
    ) {}

    override fun onSearchRequested(): Boolean {
        return false
    }

    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean {
        return false
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
        return null
    }

    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int,
    ): ActionMode? {
        return null
    }

    override fun onActionModeStarted(mode: ActionMode?) {}

    override fun onActionModeFinished(mode: ActionMode?) {}
}
