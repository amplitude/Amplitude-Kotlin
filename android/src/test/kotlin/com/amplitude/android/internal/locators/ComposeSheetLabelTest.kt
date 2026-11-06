@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.amplitude.android.internal.locators

import ComposeLayoutNodeBoundsHelper
import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.ValueElement
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.gestures.GestureActions
import com.amplitude.android.internal.resolvedFor
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ComposeSheetLabelTest {
    @BeforeEach
    fun setUp() {
        mockkConstructor(ComposeLayoutNodeBoundsHelper::class)
        every { anyConstructed<ComposeLayoutNodeBoundsHelper>().layoutNodeBoundsContain(any(), any()) } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @ParameterizedTest
    @ValueSource(strings = ["clickable", "draggable"])
    fun `sheet control does not inherit scrim accessibility label`(gesture: String) {
        val scrim = node(semantics("ContentDescription" to listOf("Close sheet")))
        val button = node(semantics("TestTag" to "follow_button"), modifier(gesture))
        val sheet = node(children = arrayOf(button))
        val resolved = locate(node(children = arrayOf(scrim, sheet)), gesture)

        assertEquals("follow_button", resolved?.tag)
        assertEquals(null, resolved?.accessibilityLabel)
    }

    @ParameterizedTest
    @ValueSource(strings = ["clickable", "draggable"])
    fun `control keeps its own accessibility label`(gesture: String) {
        val scrim = node(semantics("ContentDescription" to listOf("Close sheet")))
        val button = node(
            semantics("TestTag" to "follow_button", "ContentDescription" to listOf("Follow", "user")),
            modifier(gesture),
        )

        val resolved = locate(node(children = arrayOf(scrim, button)), gesture)

        assertEquals("follow_button", resolved?.tag)
        assertEquals("Follow, user", resolved?.accessibilityLabel)
    }

    @ParameterizedTest
    @ValueSource(strings = ["clickable", "draggable"])
    fun `control inherits metadata from its ancestors`(gesture: String) {
        val button = node(modifier(gesture))
        val sheet = node(
            semantics("TestTag" to "sheet", "ContentDescription" to listOf("Sheet actions")),
            children = arrayOf(button),
        )
        val scrim = node(semantics("TestTag" to "scrim", "ContentDescription" to listOf("Close sheet")))

        val resolved = locate(node(children = arrayOf(sheet, scrim)), gesture)

        assertEquals("sheet", resolved?.tag)
        assertEquals("Sheet actions", resolved?.accessibilityLabel)
    }

    @ParameterizedTest
    @ValueSource(strings = ["clickable", "draggable"])
    fun `control does not inherit tag from a sibling`(gesture: String) {
        val sibling = node(semantics("TestTag" to "other_button"))
        val button = node(semantics("ContentDescription" to listOf("Follow user")), modifier(gesture))

        val resolved = locate(node(children = arrayOf(sibling, button)), gesture)

        assertEquals(null, resolved?.tag)
        assertEquals("Follow user", resolved?.accessibilityLabel)
    }

    private fun locate(root: LayoutNode, gesture: String): ViewTarget? {
        val owner = mockk<Owner>()
        every { owner.root } returns root
        val target = with(ComposeViewTargetLocator(mockk<Logger>(relaxed = true))) {
            owner.locate(10f to 10f, ViewTarget.Type.Clickable)
        }
        val action = if (gesture == "clickable") GestureActions.TOUCH else GestureActions.PAN
        return target?.resolvedFor(action)
    }

    private fun node(vararg modifiers: Modifier, children: Array<LayoutNode> = emptyArray()): LayoutNode {
        val node = mockk<LayoutNode>()
        every { node.isPlaced } returns true
        every { node.zSortedChildren } returns mutableVectorOf(*children)
        every { node.getModifierInfo() } returns modifiers.map { modifier ->
            mockk<ModifierInfo>().also { every { it.modifier } returns modifier }
        }
        return node
    }

    private fun semantics(vararg properties: Pair<String, Any>): Modifier =
        modifier("semantics", ValueElement("properties", linkedMapOf(*properties)))

    private fun modifier(name: String, vararg elements: ValueElement): Modifier =
        object : Modifier.Element, InspectableValue {
            override val nameFallback: String = name
            override val valueOverride: Any? = null
            override val inspectableElements: Sequence<ValueElement> = elements.asSequence()
        }
}
