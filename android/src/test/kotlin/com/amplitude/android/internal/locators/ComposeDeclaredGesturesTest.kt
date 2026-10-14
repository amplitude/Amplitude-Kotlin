package com.amplitude.android.internal.locators

import com.amplitude.android.internal.GestureOwner
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.gestures.GestureActions
import com.amplitude.android.internal.resolvedFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ComposeDeclaredGesturesTest {
    @Test
    fun `clickable only declares tap`() {
        assertEquals(
            setOf(GestureActions.TOUCH),
            composeDeclaredGestures("clickable", "androidx.compose.foundation.ClickableElement", hasLongClick = false),
        )
    }

    @Test
    fun `combinedClickable declares long press only when a handler is set`() {
        val className = "androidx.compose.foundation.CombinedClickableElement"

        assertEquals(
            setOf(GestureActions.TOUCH),
            composeDeclaredGestures("combinedClickable", className, hasLongClick = false),
        )
        assertEquals(
            setOf(GestureActions.TOUCH, GestureActions.LONG_PRESS),
            composeDeclaredGestures("combinedClickable", className, hasLongClick = true),
        )
    }

    @Test
    fun `draggable declares pan`() {
        assertEquals(
            setOf(GestureActions.PAN),
            composeDeclaredGestures(null, "androidx.compose.foundation.gestures.DraggableElement", hasLongClick = false),
        )
        assertEquals(
            setOf(GestureActions.PAN),
            composeDeclaredGestures("anchoredDraggable", "unknown", hasLongClick = false),
        )
        assertEquals(
            setOf(GestureActions.PAN),
            composeDeclaredGestures(
                null,
                "androidx.compose.foundation.gestures.AnchoredDraggableElement",
                hasLongClick = false,
            ),
        )
        assertEquals(
            setOf(GestureActions.PAN),
            composeDeclaredGestures("swipeable", "unknown", hasLongClick = false),
        )
    }

    @Test
    fun `transformable declares pinch and rotation`() {
        assertEquals(GestureActions.TRANSFORM, composeDeclaredGestures("transformable", "unknown", hasLongClick = false))
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["clickable", "combinedClickable", "draggable", "draggable2D", "anchoredDraggable", "swipeable", "transformable"],
    )
    fun `disabled modifiers declare no gestures`(name: String) {
        assertEquals(
            emptySet<String>(),
            composeDeclaredGestures(name, "unknown", hasLongClick = true, enabled = false),
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "androidx.compose.foundation.ClickableElement",
            "androidx.compose.foundation.CombinedClickableElement",
            "androidx.compose.foundation.gestures.DraggableElement",
            "androidx.compose.foundation.gestures.Draggable2DElement",
            "androidx.compose.foundation.gestures.AnchoredDraggableElement",
            "androidx.compose.foundation.gestures.TransformableElement",
        ],
    )
    fun `disabled modifier class fallbacks declare no gestures`(className: String) {
        assertEquals(
            emptySet<String>(),
            composeDeclaredGestures(null, className, hasLongClick = true, enabled = false),
        )
    }

    @Test
    fun `enabled ancestor handles a drag when the child modifier is disabled`() {
        val target =
            target(
                GestureOwner(composeDeclaredGestures("draggable", "unknown", false, enabled = true), "parent", null),
                GestureOwner(composeDeclaredGestures("draggable", "unknown", false, enabled = false), "child", null),
            )

        assertEquals("parent", target.resolvedFor(GestureActions.PAN)?.tag)
    }

    @Test
    fun `resolvedFor reports the innermost node that declared the action`() {
        val target =
            target(
                GestureOwner(setOf(GestureActions.TOUCH), "parent", null),
                GestureOwner(setOf(GestureActions.PAN), "slider", null),
            )

        assertEquals("parent", target.resolvedFor(GestureActions.TOUCH)?.tag)
        assertEquals("slider", target.resolvedFor(GestureActions.PAN)?.tag)
        assertNull(target.resolvedFor(GestureActions.LONG_PRESS))
    }

    @Test
    fun `pointerInput is opaque and declares nothing`() {
        assertEquals(emptySet<String>(), composeDeclaredGestures("pointerInput", "unknown", hasLongClick = false))
    }

    private fun target(vararg owners: GestureOwner): ViewTarget =
        ViewTarget(
            _view = null,
            className = null,
            resourceName = null,
            tag = owners.last().tag,
            text = null,
            accessibilityLabel = null,
            source = "jetpack_compose",
            hierarchy = null,
        ).apply {
            gestureOwners = owners.toList()
        }
}
