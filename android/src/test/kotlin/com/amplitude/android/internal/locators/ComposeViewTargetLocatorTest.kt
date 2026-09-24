package com.amplitude.android.internal.locators

import com.amplitude.android.internal.InteractionAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComposeViewTargetLocatorTest {
    @Test
    fun `continues to recognize clickable modifiers as interactive`() {
        assertTrue(isInteractiveComposeModifier(nameFallback = "clickable", className = "other"))
    }

    @Test
    fun `recognizes selectable inspectable modifiers as interactive`() {
        assertTrue(isInteractiveComposeModifier(nameFallback = "selectable", className = "other"))
    }

    @Test
    fun `recognizes toggleable inspectable modifiers as interactive`() {
        assertTrue(isInteractiveComposeModifier(nameFallback = "toggleable", className = "other"))
        assertEquals(
            ComposeModifierInteractionKind.Toggle,
            composeModifierInteractionKind(nameFallback = "toggleable", className = "other"),
        )
    }

    @Test
    fun `recognizes tri-state toggleable inspectable modifiers as interactive`() {
        assertTrue(isInteractiveComposeModifier(nameFallback = "triStateToggleable", className = "other"))
    }

    @Test
    fun `recognizes selectable node elements as interactive`() {
        assertTrue(
            isInteractiveComposeModifier(
                nameFallback = null,
                className = "androidx.compose.foundation.selection.SelectableElement",
            ),
        )
        assertEquals(
            ComposeModifierInteractionKind.Select,
            composeModifierInteractionKind(
                nameFallback = null,
                className = "androidx.compose.foundation.selection.SelectableElement",
            ),
        )
    }

    @Test
    fun `recognizes toggleable node elements as interactive`() {
        assertTrue(
            isInteractiveComposeModifier(
                nameFallback = null,
                className = "androidx.compose.foundation.selection.ToggleableElement",
            ),
        )
    }

    @Test
    fun `recognizes tri-state toggleable node elements as interactive`() {
        assertTrue(
            isInteractiveComposeModifier(
                nameFallback = null,
                className = "androidx.compose.foundation.selection.TriStateToggleableElement",
            ),
        )
    }

    @Test
    fun `does not treat unrelated modifiers as interactive`() {
        assertFalse(isInteractiveComposeModifier(nameFallback = "semantics", className = "other"))
    }

    @Test
    fun `maps toggleable controls to value change`() {
        assertEquals(
            InteractionAction.ValueChange,
            composeInteractionAction(ComposeModifierInteractionKind.Toggle, hasValueChangeRole = false),
        )
    }

    @Test
    fun `maps selectable value controls to value change`() {
        assertEquals(
            InteractionAction.ValueChange,
            composeInteractionAction(ComposeModifierInteractionKind.Select, hasValueChangeRole = true),
        )
    }

    @Test
    fun `keeps selectable navigation controls as touch`() {
        assertEquals(
            InteractionAction.Touch,
            composeInteractionAction(ComposeModifierInteractionKind.Select, hasValueChangeRole = false),
        )
    }
}
