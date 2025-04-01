package com.amplitude.android.internal

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import com.amplitude.android.internal.locators.ViewTargetLocator
import com.amplitude.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Utility class for scanning the view hierarchy and finding a target at a given position.
 */
internal object ViewHierarchyScanner {
    /**
     * Finds a target in the view hierarchy at the given position and returns a [ViewTarget].
     *
     * If the found target is clickable and its [View] contains another clickable direct child in the
     * target position, the child will be returned.
     *
     * @param targetPosition the position (x, y) to find the target at
     * @param targetType the type of the target to find
     * @param viewTargetLocators the locators to use to find the target
     * @return the [ViewTarget] at the given position, or null if none was found
     */
    @JvmStatic
    fun View.findTarget(
        targetPosition: Pair<Float, Float>,
        viewTargetLocators: List<ViewTargetLocator>,
        targetType: ViewTarget.Type,
        logger: Logger,
    ): ViewTarget? =
        runBlocking {
            val viewLooper =
                handler?.looper
                    ?: Looper.getMainLooper()
                    ?: logger.error("Unable to get main looper")
                        .let { return@runBlocking null }

            // The entire view tree is single threaded, and that's typically the main thread, but
            // it doesn't have to be, and we don't know where the passed in view is coming from.
            if (viewLooper.thread == Thread.currentThread()) {
                findTargetWithLocators(targetPosition, targetType, viewTargetLocators, logger)
            } else {
                withContext(Dispatchers.Main) {
                    findTargetWithLocators(targetPosition, targetType, viewTargetLocators, logger)
                }
            }
        }

    /** Applies the locators to the view hierarchy to find the target */
    private fun View.findTargetWithLocators(
        targetPosition: Pair<Float, Float>,
        targetType: ViewTarget.Type,
        viewTargetLocators: List<ViewTargetLocator>,
        logger: Logger,
    ): ViewTarget? {
        val queue = ArrayDeque<View>().apply { add(this@findTargetWithLocators) }

        var target: ViewTarget? = null
        while (queue.isNotEmpty()) {
            val view =
                try {
                    queue.removeFirst()
                } catch (e: NoSuchElementException) {
                    logger.error("Unable to get view from queue")
                    continue
                }

            if (view is ViewGroup) {
                queue.addAll(view.children)
            }
            try {
                // Applies the locators until a target is found. If the target type is clickable, check
                // the children in case the target is a child which is also clickable.
                viewTargetLocators.any { locator ->
                    with(locator) {
                        view.locate(targetPosition, targetType)?.let { newTarget ->
                            if (targetType == ViewTarget.Type.Clickable) {
                                target = newTarget
                                return@any true
                            } else {
                                return newTarget
                            }
                        }
                        false
                    }
                }
            } catch (t: Throwable) {
                logger.error("Error while locating target")
            }
        }

        return target
    }
}
