package com.amplitude.android.internal.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Internal ModifierNodeElement for Amplitude frustration analytics ignore functionality.
 */
internal data class AmpFrustrationIgnoreElement(
    val ignoreRageClick: Boolean,
    val ignoreDeadClick: Boolean,
) : ModifierNodeElement<AmpFrustrationIgnoreNode>() {
    override fun create(): AmpFrustrationIgnoreNode {
        return AmpFrustrationIgnoreNode(ignoreRageClick, ignoreDeadClick)
    }

    override fun update(node: AmpFrustrationIgnoreNode) {
        node.ignoreRageClick = ignoreRageClick
        node.ignoreDeadClick = ignoreDeadClick
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "ampIgnoreFrustrationAnalytics"
        properties["ignoreRageClick"] = ignoreRageClick
        properties["ignoreDeadClick"] = ignoreDeadClick
    }
}

/**
 * Internal ModifierNode to hold configuration for Amplitude frustration analytics.
 */
internal class AmpFrustrationIgnoreNode(
    var ignoreRageClick: Boolean,
    var ignoreDeadClick: Boolean,
) : Modifier.Node()
