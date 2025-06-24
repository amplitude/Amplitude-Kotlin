package com.amplitude.android.internal.locators;

import androidx.annotation.OptIn;
import androidx.compose.ui.InternalComposeUiApi;
import androidx.compose.ui.Modifier;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.Owner;
import androidx.compose.ui.platform.InspectableValue;
import androidx.compose.ui.platform.ValueElement;

import com.amplitude.android.internal.ViewTarget;
import com.amplitude.common.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
                    final Modifier modifier = modifierInfo.getModifier();
                    if (modifier instanceof InspectableValue) {
                        final InspectableValue inspectableValue = (InspectableValue) modifier;
                        if ("testTag".equals(inspectableValue.getNameFallback())) {
                            Iterator<ValueElement> iterator = inspectableValue.getInspectableElements().iterator();
                            while (iterator.hasNext()) {
                                final ValueElement element = iterator.next();
                                if ("tag".equals(element.getName())) {
                                    lastKnownTag = (String) element.getValue();
                                    break;
                                }
                            }
                        } else if ("semantics".equals(inspectableValue.getNameFallback())) {
                            Iterator<ValueElement> iterator = inspectableValue.getInspectableElements().iterator();
                            while (iterator.hasNext()) {
                                final ValueElement element = iterator.next();
                                if ("properties".equals(element.getName())) {
                                    final Object elementValue = element.getValue();
                                    if (elementValue instanceof LinkedHashMap) {
                                        final LinkedHashMap<Object, Object> properties = (LinkedHashMap<Object, Object>) elementValue;
                                        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                                            if ("TestTag".equals(entry.getKey())) {
                                                lastKnownTag = (String) entry.getValue();
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if ("clickable".equals(inspectableValue.getNameFallback())) {
                            isClickable = true;
                        } else {
                            final @Nullable String type = modifier.getClass().getCanonicalName();
                            if ("androidx.compose.foundation.ClickableElement".equals(type)
                                    || "androidx.compose.foundation.CombinedClickableElement".equals(type)) {
                                isClickable = true;
                            }
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
            return new ViewTarget(null, null, null, targetTag, null, SOURCE, null);
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
