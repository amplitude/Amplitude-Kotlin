package com.amplitude.android.internal.gestures

import android.annotation.SuppressLint
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

internal open class WindowCallbackAdapter(val delegate: Window.Callback) : Window.Callback {
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        return delegate.dispatchKeyEvent(event)
    }

    override fun dispatchKeyShortcutEvent(event: KeyEvent?): Boolean {
        return delegate.dispatchKeyShortcutEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        return delegate.dispatchTouchEvent(event)
    }

    override fun dispatchTrackballEvent(event: MotionEvent?): Boolean {
        return delegate.dispatchTrackballEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        return delegate.dispatchGenericMotionEvent(event)
    }

    override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent?): Boolean {
        return delegate.dispatchPopulateAccessibilityEvent(event)
    }

    override fun onCreatePanelView(featureId: Int): View? {
        return delegate.onCreatePanelView(featureId)
    }

    override fun onCreatePanelMenu(
        featureId: Int,
        menu: Menu,
    ): Boolean {
        return delegate.onCreatePanelMenu(featureId, menu)
    }

    override fun onPreparePanel(
        featureId: Int,
        view: View?,
        menu: Menu,
    ): Boolean {
        return delegate.onPreparePanel(featureId, view, menu)
    }

    override fun onMenuOpened(
        featureId: Int,
        menu: Menu,
    ): Boolean {
        return delegate.onMenuOpened(featureId, menu)
    }

    override fun onMenuItemSelected(
        featureId: Int,
        item: MenuItem,
    ): Boolean {
        return delegate.onMenuItemSelected(featureId, item)
    }

    override fun onWindowAttributesChanged(attrs: WindowManager.LayoutParams?) {
        return delegate.onWindowAttributesChanged(attrs)
    }

    override fun onContentChanged() {
        return delegate.onContentChanged()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        return delegate.onWindowFocusChanged(hasFocus)
    }

    override fun onAttachedToWindow() {
        return delegate.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        return delegate.onDetachedFromWindow()
    }

    override fun onPanelClosed(
        featureId: Int,
        menu: Menu,
    ) {
        return delegate.onPanelClosed(featureId, menu)
    }

    override fun onSearchRequested(): Boolean {
        return delegate.onSearchRequested()
    }

    @SuppressLint("NewApi")
    override fun onSearchRequested(searchEvent: SearchEvent?): Boolean {
        return delegate.onSearchRequested(searchEvent)
    }

    override fun onWindowStartingActionMode(callback: ActionMode.Callback?): ActionMode? {
        return delegate.onWindowStartingActionMode(callback)
    }

    @SuppressLint("NewApi")
    override fun onWindowStartingActionMode(
        callback: ActionMode.Callback?,
        type: Int,
    ): ActionMode? {
        return delegate.onWindowStartingActionMode(callback, type)
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        return delegate.onActionModeStarted(mode)
    }

    override fun onActionModeFinished(mode: ActionMode?) {
        return delegate.onActionModeFinished(mode)
    }
}
