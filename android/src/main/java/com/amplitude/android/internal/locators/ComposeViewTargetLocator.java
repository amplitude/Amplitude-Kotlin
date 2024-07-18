package com.amplitude.android.internal.locators;

import androidx.annotation.OptIn;
import androidx.compose.ui.InternalComposeUiApi;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.Owner;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsModifier;
import androidx.compose.ui.semantics.SemanticsPropertyKey;

import com.amplitude.android.internal.ViewTarget;
import com.amplitude.common.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import kotlin.Pair;

@SuppressWarnings("KotlinInternalInJava")
@OptIn(markerClass = InternalComposeUiApi.class)
public class ComposeViewTargetLocator implements ViewTargetLocator {
    private volatile @Nullable ComposeLayoutNodeBoundsHelper composeLayoutNodeBoundsHelper;
    private static final String SOURCE = "jetpack_compose";
    private final @NotNull Logger logger;

    public ComposeViewTargetLocator(@NotNull Logger logger) {
        this.logger = logger;
    }

    @Nullable
    @Override
    public ViewTarget locate(
            @NotNull Object root,
            @NotNull Pair<Float, Float> targetPosition,
            @NotNull ViewTarget.Type targetType) {

        // lazy init composeHelper as it's using some reflection under the hood
        if (composeLayoutNodeBoundsHelper == null) {
            synchronized (this) {
                if (composeLayoutNodeBoundsHelper == null) {
                    composeLayoutNodeBoundsHelper = new ComposeLayoutNodeBoundsHelper(logger);
                }
            }
        }

        if (!(root instanceof Owner)) {
            return null;
        }

        final @NotNull Queue<LayoutNode> queue = new ArrayDeque<>();
        queue.add(((Owner) root).getRoot());

        // the final tag to return
        @Nullable String targetTag = null;

        // the last known tag when iterating the node tree
        @Nullable String lastKnownTag = null;
        while (!queue.isEmpty()) {
            final @Nullable LayoutNode node = queue.poll();
            if (node == null) {
                continue;
            }

            if (node.isPlaced() && layoutNodeBoundsContain(composeLayoutNodeBoundsHelper, node, targetPosition.component1(), targetPosition.component2())) {
                boolean isClickable = false;
                final List<ModifierInfo> modifiers = node.getModifierInfo();
                for (ModifierInfo modifierInfo : modifiers) {
                    if (modifierInfo.getModifier() instanceof SemanticsModifier) {
                        final SemanticsModifier semanticsModifierCore =
                                (SemanticsModifier) modifierInfo.getModifier();
                        final SemanticsConfiguration semanticsConfiguration =
                                semanticsModifierCore.getSemanticsConfiguration();
                        for (Map.Entry<? extends SemanticsPropertyKey<?>, ?> entry : semanticsConfiguration) {
                            final @Nullable String key = entry.getKey().getName();
                            if ("OnClick".equals(key)) {
                                isClickable = true;
                            } else if ("TestTag".equals(key)) {
                                if (entry.getValue() instanceof String) {
                                    lastKnownTag = (String) entry.getValue();
                                }
                            }
                        }
                    } else {
                        // Newer Jetpack Compose 1.5 uses Node modifiers for clicks/scrolls
                        final @Nullable String type = modifierInfo.getModifier().getClass().getCanonicalName();
                        if ("androidx.compose.foundation.ClickableElement".equals(type)
                                || "androidx.compose.foundation.CombinedClickableElement".equals(type)) {
                            isClickable = true;
                        }
                    }
                }

                if (isClickable && targetType == ViewTarget.Type.Clickable) {
                    targetTag = lastKnownTag;
                }
            }
            queue.addAll(node.getZSortedChildren().asMutableList());
        }

        if (targetTag == null) {
            return null;
        } else {
            return new ViewTarget(null, null, null, targetTag, SOURCE);
        }
    }

    private static boolean layoutNodeBoundsContain(
            @NotNull ComposeLayoutNodeBoundsHelper composeLayoutNodeBoundsHelper,
            @NotNull LayoutNode node,
            final float x,
            final float y) {

        final @Nullable Rect bounds = composeLayoutNodeBoundsHelper.getLayoutNodeWindowBounds(node);
        if (bounds == null) {
            return false;
        } else {
            return x >= bounds.getLeft()
                    && x <= bounds.getRight()
                    && y >= bounds.getTop()
                    && y <= bounds.getBottom();
        }
    }
}
