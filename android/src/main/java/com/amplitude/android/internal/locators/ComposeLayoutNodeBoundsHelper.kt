@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal LayoutNode class

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.LayoutNode
import com.amplitude.common.Logger

internal class ComposeLayoutNodeBoundsHelper(private val logger: Logger) {

    private fun getLayoutNodeWindowBounds(node: LayoutNode): Rect? {
        return try {
            // Prefer modifier coordinates if available, otherwise fallback to node coordinates
            val modifierInfo = node.getModifierInfo().firstOrNull()
            val coordinates = modifierInfo?.coordinates ?: node.coordinates
            coordinates.boundsInWindow()
        } catch (e: Exception) {
            logger.warn("Could not fetch position for LayoutNode")
            null
        }
    }

    internal fun layoutNodeBoundsContain(node: LayoutNode, position: Pair<Float, Float>): Boolean {
        val bounds = getLayoutNodeWindowBounds(node) ?: return false
        val (x, y) = position
        return bounds.contains(Offset(x, y))
    }
}
