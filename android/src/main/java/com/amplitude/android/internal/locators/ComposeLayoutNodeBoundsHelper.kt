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
                .takeIf { it?.coordinates?.isAttached == true }

            // Fallback: find any attached modifier
            val attachedModifier by lazy {
                modifierInfo.find { it.coordinates.isAttached }
            }

            // Last resort: use node coordinates
            val modifierCoordinates = (firstModifier ?: attachedModifier)?.coordinates
            return (modifierCoordinates ?: node.coordinates).boundsInWindow()
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
