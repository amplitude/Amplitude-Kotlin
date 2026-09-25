package com.amplitude.android.internal.locators

import com.amplitude.android.internal.GestureOwner
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.gestures.GestureActions
import com.amplitude.android.internal.resolvedFor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

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
    }

    @Test
    fun `transformable declares pinch and rotation`() {
        assertEquals(GestureActions.TRANSFORM, composeDeclaredGestures("transformable", "unknown", hasLongClick = false))
    }

    @Test
    fun `resolvedFor reports the innermost node that declared the action`() {
        val target =
            ViewTarget(
                _view = null,
                className = null,
                resourceName = null,
                tag = "slider",
                text = null,
                accessibilityLabel = null,
                source = "jetpack_compose",
                hierarchy = null,
            ).apply {
                gestureOwners =
                    listOf(
                        GestureOwner(setOf(GestureActions.TOUCH), "parent", null),
                        GestureOwner(setOf(GestureActions.PAN), "slider", null),
                    )
            }

        assertEquals("parent", target.resolvedFor(GestureActions.TOUCH)?.tag)
        assertEquals("slider", target.resolvedFor(GestureActions.PAN)?.tag)
        assertNull(target.resolvedFor(GestureActions.LONG_PRESS))
    }

    @Test
    fun `pointerInput is opaque and declares nothing`() {
        assertEquals(emptySet<String>(), composeDeclaredGestures("pointerInput", "unknown", hasLongClick = false))
    }
}
