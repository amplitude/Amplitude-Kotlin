package com.amplitude.android.internal.locators

import android.view.View
import android.widget.Button
import com.amplitude.android.internal.ViewResourceUtils.resourceIdWithFallback
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.ViewTarget.Type

internal class AndroidViewTargetLocator : ViewTargetLocator {
    private val coordinates = IntArray(2)

    companion object {
        private const val HIERARCHY_DELIMITER = " → "

        private const val SOURCE = "android_view"
    }

    override fun Any.locate(
        targetPosition: Pair<Float, Float>,
        targetType: Type,
    ): ViewTarget? {
        return (this as? View)
            ?.takeIf { touchWithinBounds(targetPosition) && targetType === Type.Clickable && isViewTappable() }
            ?.let { createViewTarget() }
    }

    private fun View.createViewTarget(): ViewTarget {
        val className = javaClass.canonicalName ?: javaClass.simpleName ?: null
        val resourceName = resourceIdWithFallback
        val hierarchy = hierarchy
        val tag = tag
            ?.takeIf { it is String || it is Number || it is Boolean || it is Char }
            ?.toString()
        val text = (this as? Button)?.text?.toString()
        return ViewTarget(this, className, resourceName, tag, text, SOURCE, hierarchy)
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

    private val View.hierarchy: String
        get() {
            val hierarchy = mutableListOf<String>()
            var currentView: View? = this
            while (currentView != null) {
                hierarchy.add(currentView.javaClass.simpleName)
                currentView = currentView.parent as? View
            }
            return hierarchy.joinToString(separator = HIERARCHY_DELIMITER)
        }
}
