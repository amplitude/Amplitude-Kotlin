package com.amplitude.android.internal.locators

import android.content.res.Resources
import android.view.View
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.ViewTarget.Type

internal class AndroidViewTargetLocator : ViewTargetLocator {
    private val coordinates = IntArray(2)

    companion object {
        private const val SOURCE = "android_view"
    }

    override fun Any.locate(
        position: Pair<Float, Float>,
        targetType: Type,
    ): ViewTarget? {
        return (this as? View)
            ?.takeIf { touchWithinBounds(position) && targetType === Type.Clickable && isViewTappable() }
            ?.let { createViewTarget() }
    }

    private fun View.createViewTarget(): ViewTarget {
        val className = javaClass.canonicalName ?: javaClass.simpleName ?: null
        val resourceName: String? =
            try {
                context.resources.getResourceEntryName(id)
            } catch (ignored: Resources.NotFoundException) {
                null
            }
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
}
