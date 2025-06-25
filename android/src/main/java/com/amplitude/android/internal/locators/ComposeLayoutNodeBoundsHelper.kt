@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal LayoutNode class

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNodeLayoutDelegate
import com.amplitude.common.Logger
import java.lang.reflect.Field

internal class ComposeLayoutNodeBoundsHelper(private val logger: Logger) {
    private var layoutDelegateField: Field? = null

    init {
        try {
            val clazz = Class.forName("androidx.compose.ui.node.LayoutNode")
            layoutDelegateField = clazz.getDeclaredField("layoutDelegate")
            layoutDelegateField?.isAccessible = true
        } catch (e: Exception) {
            logger.info("Could not find LayoutNode.layoutDelegate field")
        }
    }

    private fun getLayoutNodeWindowBounds(node: LayoutNode): Rect? {
        val field = layoutDelegateField ?: return null

        return try {
            val delegate = field.get(node) as? LayoutNodeLayoutDelegate ?: return null
            delegate.outerCoordinator.coordinates.boundsInWindow()
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
