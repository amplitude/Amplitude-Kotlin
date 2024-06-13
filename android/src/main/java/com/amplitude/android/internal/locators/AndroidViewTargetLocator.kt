package com.amplitude.android.internal.locators

import android.view.View
import android.widget.AbsListView
import android.widget.ScrollView
import androidx.core.view.ScrollingView
import com.amplitude.android.internal.ViewResourceUtils.resourceIdWithFallback
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.ViewTarget.Type

internal class AndroidViewTargetLocator(
    private val isAndroidXAvailable: Boolean,
) : ViewTargetLocator {
    private val coordinates = IntArray(2)

    companion object {
        private const val SOURCE = "android_view"
    }

    override fun Any.locate(
        targetPosition: Pair<Float, Float>,
        targetType: Type,
    ): ViewTarget? {
        val view = this as? View ?: return null
        with(view) {
            if (!touchWithinBounds(targetPosition)) {
                return null
            }

            if (targetType === Type.Clickable && isViewTappable()) {
                return createViewTarget()
            } else if (targetType === Type.Scrollable && isViewScrollable(isAndroidXAvailable)) {
                return createViewTarget()
            }
        }
        return null
    }

    private fun View.createViewTarget(): ViewTarget {
        val className = javaClass.canonicalName ?: javaClass.simpleName ?: null
        val resourceName = resourceIdWithFallback
        return ViewTarget(this, className, resourceName, null, SOURCE)
    }

    private fun View.touchWithinBounds(position: Pair<Float, Float>): Boolean {
        val (x, y) = position

        getLocationOnScreen(coordinates)
        val vx = coordinates[0]
        val vy = coordinates[1]

        val w = width
        val h = height

        return !(x < vx || x > vx + w || y < vy || y > vy + h)
    }

    private fun View.isViewTappable(): Boolean {
        return isClickable && visibility == View.VISIBLE
    }

    private fun View.isViewScrollable(isAndroidXAvailable: Boolean): Boolean {
        return (
            (
                isJetpackScrollingView(isAndroidXAvailable) ||
                    AbsListView::class.java.isAssignableFrom(this.javaClass) ||
                    ScrollView::class.java.isAssignableFrom(this.javaClass)
                ) &&
                visibility == View.VISIBLE
            )
    }

    private fun View.isJetpackScrollingView(isAndroidXAvailable: Boolean): Boolean {
        if (!isAndroidXAvailable) {
            return false
        }
        return ScrollingView::class.java.isAssignableFrom(this.javaClass)
    }
}
