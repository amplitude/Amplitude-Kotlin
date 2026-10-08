@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal LayoutNode class

package com.amplitude.android.internal.locators

import ComposeLayoutNodeBoundsHelper
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.semantics.Role
import com.amplitude.android.internal.GestureOwner
import com.amplitude.android.internal.InteractionAction
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.compose.AmpFrustrationIgnoreElement
import com.amplitude.android.internal.gestures.GestureActions
import com.amplitude.common.Logger
import java.util.ArrayDeque
import java.util.Queue

internal class ComposeViewTargetLocator(private val logger: Logger) : ViewTargetLocator {
    private val composeLayoutNodeBoundsHelper by lazy {
        ComposeLayoutNodeBoundsHelper(logger)
    }

    internal companion object {
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

        // the final accessibility label to return
        var targetAccessibilityLabel: String? = null

        // track if we found an element that declared any gesture handler
        var foundClickableElement = false

        // every interactive node under the touch, since a gesture a child does not consume is
        // still delivered to its ancestors
        val gestureOwners = mutableListOf<GestureOwner>()

        // the last known tag when iterating the node tree
        var lastKnownTag: String? = null

        // the last known accessibility label when iterating the node tree
        var lastKnownAccessibilityLabel: String? = null

        // Amplitude frustration analytics configuration (extracted as simple booleans)
        var ignoreRageClick = false
        var ignoreDeadClick = false

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue

            if (node.isPlaced &&
                composeLayoutNodeBoundsHelper.layoutNodeBoundsContain(node, targetPosition)
            ) {
                val nodeGestures = mutableSetOf<String>()
                var hasSelectableModifier = false
                var hasValueChangeRole = false
                var interactionAction = InteractionAction.Touch
                val modifiers = node.getModifierInfo()

                for (modifierInfo in modifiers) {
                    val modifier = modifierInfo.modifier

                    // Check for Amplitude frustration analytics configuration
                    if (modifier is AmpFrustrationIgnoreElement) {
                        ignoreRageClick = modifier.ignoreRageClick
                        ignoreDeadClick = modifier.ignoreDeadClick
                    }

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
                                                when (key.toString()) {
                                                    "TestTag" -> {
                                                        lastKnownTag = value as? String
                                                    }
                                                    "ContentDescription" -> {
                                                        // ContentDescription is a List<String> in Compose semantics
                                                        lastKnownAccessibilityLabel =
                                                            (value as? List<*>)
                                                                ?.filterIsInstance<String>()
                                                                ?.joinToString(", ")
                                                    }
                                                    "Role" -> {
                                                        hasValueChangeRole = value.isValueChangeRole()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        nodeGestures.addAll(
                            composeDeclaredGestures(
                                nameFallback = modifier.nameFallback,
                                className = modifier.javaClass.name,
                                hasLongClick = modifier.hasLongClickHandler(),
                            ),
                        )
                    }

                    when (composeModifierInteractionKind(modifier)) {
                        ComposeModifierInteractionKind.Toggle -> {
                            nodeGestures += GestureActions.VALUE_CHANGE
                            interactionAction = InteractionAction.ValueChange
                        }
                        ComposeModifierInteractionKind.Select -> {
                            hasSelectableModifier = true
                            nodeGestures += GestureActions.TOUCH
                            hasValueChangeRole = hasValueChangeRole || modifier.inspectableRole().isValueChangeRole()
                        }
                        ComposeModifierInteractionKind.Click,
                        ComposeModifierInteractionKind.None,
                        -> Unit
                    }
                }

                if (hasSelectableModifier && hasValueChangeRole) {
                    nodeGestures -= GestureActions.TOUCH
                    nodeGestures += GestureActions.VALUE_CHANGE
                    interactionAction = InteractionAction.ValueChange
                }

                if (nodeGestures.isNotEmpty() && targetType == ViewTarget.Type.Clickable) {
                    foundClickableElement = true
                    targetTag = lastKnownTag // can be null if no test tag is found
                    targetAccessibilityLabel = lastKnownAccessibilityLabel // can be null
                    gestureOwners +=
                        GestureOwner(
                            actions = nodeGestures.toSet(),
                            tag = lastKnownTag,
                            accessibilityLabel = lastKnownAccessibilityLabel,
                            interactionAction = interactionAction,
                        )
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
                accessibilityLabel = targetAccessibilityLabel,
                source = SOURCE,
                hierarchy = null,
                ampIgnoreRageClick = ignoreRageClick,
                ampIgnoreDeadClick = ignoreDeadClick,
            ).apply {
                this.gestureOwners = gestureOwners
                interactionAction = gestureOwners.last().interactionAction
            }
        }
    }

    private fun InspectableValue.hasLongClickHandler(): Boolean =
        inspectableElements.any { it.name == "onLongClick" && it.value != null }
}

/**
 * Maps a Compose modifier to the gesture actions it handles. Only modifiers that install a
 * gesture handler count; a `pointerInput` block is opaque and cannot be classified.
 */
internal fun composeDeclaredGestures(
    nameFallback: String?,
    className: String,
    hasLongClick: Boolean,
): Set<String> =
    when {
        nameFallback == "clickable" ||
            className == "androidx.compose.foundation.ClickableElement" -> {
            setOf(GestureActions.TOUCH)
        }
        nameFallback == "combinedClickable" ||
            className == "androidx.compose.foundation.CombinedClickableElement" -> {
            if (hasLongClick) setOf(GestureActions.TOUCH, GestureActions.LONG_PRESS) else setOf(GestureActions.TOUCH)
        }
        nameFallback == "draggable" ||
            nameFallback == "draggable2D" ||
            nameFallback == "anchoredDraggable" ||
            className == "androidx.compose.foundation.gestures.DraggableElement" ||
            className == "androidx.compose.foundation.gestures.Draggable2DElement" -> {
            setOf(GestureActions.PAN)
        }
        nameFallback == "transformable" ||
            className == "androidx.compose.foundation.gestures.TransformableElement" -> {
            GestureActions.TRANSFORM
        }
        else -> emptySet()
    }

internal fun isInteractiveComposeModifier(modifier: Any): Boolean =
    composeModifierInteractionKind(modifier) != ComposeModifierInteractionKind.None

internal fun composeModifierInteractionKind(modifier: Any): ComposeModifierInteractionKind =
    composeModifierInteractionKind(
        nameFallback = (modifier as? InspectableValue)?.nameFallback,
        className = modifier.javaClass.name,
    )

internal fun isInteractiveComposeModifier(
    nameFallback: String?,
    className: String,
): Boolean = composeModifierInteractionKind(nameFallback, className) != ComposeModifierInteractionKind.None

internal fun composeModifierInteractionKind(
    nameFallback: String?,
    className: String,
): ComposeModifierInteractionKind =
    when {
        nameFallback == "toggleable" ||
            nameFallback == "triStateToggleable" ||
            className == "androidx.compose.foundation.selection.ToggleableElement" ||
            className == "androidx.compose.foundation.selection.TriStateToggleableElement" -> {
            ComposeModifierInteractionKind.Toggle
        }
        nameFallback == "selectable" ||
            className == "androidx.compose.foundation.selection.SelectableElement" -> {
            ComposeModifierInteractionKind.Select
        }
        nameFallback == "clickable" ||
            nameFallback == "combinedClickable" ||
            className == "androidx.compose.foundation.ClickableElement" ||
            className == "androidx.compose.foundation.CombinedClickableElement" -> {
            ComposeModifierInteractionKind.Click
        }
        else -> ComposeModifierInteractionKind.None
    }

internal fun composeInteractionAction(
    interactionKind: ComposeModifierInteractionKind,
    hasValueChangeRole: Boolean,
): InteractionAction =
    when {
        interactionKind == ComposeModifierInteractionKind.Toggle -> InteractionAction.ValueChange
        interactionKind == ComposeModifierInteractionKind.Select && hasValueChangeRole -> InteractionAction.ValueChange
        else -> InteractionAction.Touch
    }

private fun Any.inspectableRole(): Any? =
    (this as? InspectableValue)
        ?.inspectableElements
        ?.firstOrNull { it.name == "role" }
        ?.value

private fun Any?.isValueChangeRole(): Boolean =
    this == Role.Checkbox ||
        this == Role.Switch ||
        this == Role.RadioButton

internal enum class ComposeModifierInteractionKind {
    None,
    Click,
    Select,
    Toggle,
}
