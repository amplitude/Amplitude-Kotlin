@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal LayoutNode class

package com.amplitude.android.internal.locators

import ComposeLayoutNodeBoundsHelper
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.platform.InspectableValue
import com.amplitude.android.internal.ViewTarget
import com.amplitude.common.Logger
import java.util.ArrayDeque
import java.util.Queue

internal class ComposeViewTargetLocator(private val logger: Logger) : ViewTargetLocator {

    private val composeLayoutNodeBoundsHelper by lazy {
        ComposeLayoutNodeBoundsHelper(logger)
    }

    companion object {
        private const val SOURCE = "jetpack_compose"
    }

    override fun Any.locate(
        targetPosition: Pair<Float, Float>,
        targetType: ViewTarget.Type,
    ): ViewTarget? {
        val root = this as? Owner ?: return null

        val queue: Queue<LayoutNode> = ArrayDeque()
        queue.add(root.root)

        // the final tag to return (can be null if no test tag is found)
        var targetTag: String? = null

        // track if we found a clickable element
        var foundClickableElement = false

        // the last known tag when iterating the node tree
        var lastKnownTag: String? = null

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue

            if (node.isPlaced &&
                composeLayoutNodeBoundsHelper.layoutNodeBoundsContain(node, targetPosition)
            ) {
                var isClickable = false
                val modifiers = node.getModifierInfo()

                for (modifierInfo in modifiers) {
                    val modifier = modifierInfo.modifier
                    if (modifier is InspectableValue) {
                        when (modifier.nameFallback) {
                            "testTag" -> {
                                for (element in modifier.inspectableElements) {
                                    if (element.name == "tag") {
                                        lastKnownTag = element.value as? String
                                        break
                                    }
                                }
                            }

                            "semantics" -> {
                                for (element in modifier.inspectableElements) {
                                    if (element.name == "properties") {
                                        val elementValue = element.value
                                        if (elementValue is LinkedHashMap<*, *>) {
                                            for ((key, value) in elementValue.entries) {
                                                if (key == "TestTag") {
                                                    lastKnownTag = value as? String
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            "clickable" -> {
                                isClickable = true
                            }
                        }

                        val type = modifier.javaClass.name
                        if (type == "androidx.compose.foundation.ClickableElement" ||
                            type == "androidx.compose.foundation.CombinedClickableElement"
                        ) {
                            isClickable = true
                        }
                    }
                }

                if (isClickable && targetType == ViewTarget.Type.Clickable) {
                    foundClickableElement = true
                    targetTag = lastKnownTag // can be null if no test tag is found
                }
            }
            queue.addAll(node.zSortedChildren.asMutableList())
        }

        return if (!foundClickableElement) {
            null
        } else {
            ViewTarget(
                _view = null,
                className = null,
                resourceName = null,
                tag = targetTag,
                text = null,
                source = SOURCE,
                hierarchy = null
            )
        }
    }
}
