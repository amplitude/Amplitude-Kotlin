@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal LayoutNode class

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.LayoutNode
import com.amplitude.common.Logger

internal class ComposeLayoutNodeBoundsHelper(private val logger: Logger) {

    private fun getLayoutNodeWindowBounds(node: LayoutNode): Rect? {
        return try {
            val modifierInfo = node.getModifierInfo()

            // Fast path: check first modifier (most common case)
            val firstModifier = modifierInfo.firstOrNull()
            if (firstModifier?.coordinates?.isAttached == true) {
                return firstModifier.coordinates.boundsInWindow()
            }

            // Fallback: find any attached modifier
            val attachedModifier = modifierInfo.find { it.coordinates.isAttached }
            if (attachedModifier != null) {
                return attachedModifier.coordinates.boundsInWindow()
            }

            // Last resort: use node coordinates if attached
            if (node.coordinates.isAttached) {
                node.coordinates.boundsInWindow()
            } else {
                null
            }
        } catch (e: Exception) {
            logger.warn("Could not fetch position for LayoutNode")
            null
        }
    }

    internal fun layoutNodeBoundsContain(
        node: LayoutNode,
        position: Pair<Float, Float>,
    ): Boolean {
        val bounds = getLayoutNodeWindowBounds(node) ?: return false
        val (x, y) = position
        return bounds.contains(Offset(x, y))
    }
}
