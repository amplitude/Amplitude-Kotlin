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
import java.util.concurrent.atomic.AtomicReference;

import kotlin.Pair;

@SuppressWarnings("KotlinInternalInJava")
@OptIn(markerClass = InternalComposeUiApi.class)
public class ComposeViewTargetLocatorWithScan implements ViewTargetLocator {
    protected volatile @Nullable ComposeLayoutNodeBoundsHelper composeLayoutNodeBoundsHelper;
    private static final String SOURCE = "jetpack_compose";
    private final @NotNull Logger logger;

    public interface ComposeLayoutNodeCallback {
        Boolean onLayoutNode(LayoutNode node, Rect bounds, List<ModifierInfo> modifiers);
    }

    public ComposeViewTargetLocatorWithScan(@NotNull Logger logger) {
        this.logger = logger;
    }

    public void scan(Object root, ComposeLayoutNodeCallback callback) {
        if (!(root instanceof Owner)) {
            return;
        }

        final @NotNull Queue<LayoutNode> queue = new ArrayDeque<>();
        queue.add(((Owner)root).getRoot());

        while (!queue.isEmpty()) {
            final @Nullable LayoutNode node = queue.poll();
            if (node == null) {
                continue;
            }

            if (!node.isPlaced()) {
                continue;
            }

            Rect bounds = composeLayoutNodeBoundsHelper.getLayoutNodeWindowBounds(node);
            if (!callback.onLayoutNode(node, bounds, node.getModifierInfo())) {
                return;
            }

            queue.addAll(node.getZSortedChildren().asMutableList());
        }
    }

//    public Boolean scanNode(LayoutNode node, Rect bounds, List<ModifierInfo> modifiers) {
//        return true;
//    }


    @Nullable
    public ComposeLayoutNodeBoundsHelper getComposeLayoutNodeBoundsHelper() {
        // lazy init composeHelper as it's using some reflection under the hood
        if (composeLayoutNodeBoundsHelper == null) {
            synchronized (this) {
                if (composeLayoutNodeBoundsHelper == null) {
                    composeLayoutNodeBoundsHelper = new ComposeLayoutNodeBoundsHelper(logger);
                }
            }
        }

        return composeLayoutNodeBoundsHelper;
    }

    @Nullable
    @Override
    public ViewTarget locate(
            @NotNull Object root,
            @NotNull Pair<Float, Float> targetPosition,
            @NotNull ViewTarget.Type targetType) {
        // lazy init composeHelper as it's using some reflection under the hood
        getComposeLayoutNodeBoundsHelper();

        // the final tag to return
        @Nullable AtomicReference<String> targetTag = null;

        // the last known tag when iterating the node tree
        @Nullable AtomicReference<String> lastKnownTag = null;

        scan(root, (node, bounds, modifiers2) -> {
            if (boundsContain(bounds, targetPosition.component1(), targetPosition.component2())) {
                boolean isClickable = false;
                boolean isScrollable = false;

                final List<ModifierInfo> modifiers = node.getModifierInfo();
                for (ModifierInfo modifierInfo : modifiers) {
                    if (modifierInfo.getModifier() instanceof SemanticsModifier) {
                        final SemanticsModifier semanticsModifierCore =
                                (SemanticsModifier) modifierInfo.getModifier();
                        final SemanticsConfiguration semanticsConfiguration =
                                semanticsModifierCore.getSemanticsConfiguration();
                        for (Map.Entry<? extends SemanticsPropertyKey<?>, ?> entry : semanticsConfiguration) {
                            final @Nullable String key = entry.getKey().getName();
                            switch (key) {
                                case "ScrollBy":
                                    isScrollable = true;
                                    break;
                                case "OnClick":
                                    isClickable = true;
                                    break;
                                case "TestTag":
                                    if (entry.getValue() instanceof String) {
                                        lastKnownTag.set((String) entry.getValue());
                                    }
                                    break;
                            }
                        }
                    } else {
                        // Newer Jetpack Compose 1.5 uses Node modifiers for clicks/scrolls
                        final @Nullable String type = modifierInfo.getModifier().getClass().getCanonicalName();
                        if ("androidx.compose.foundation.ClickableElement".equals(type)
                                || "androidx.compose.foundation.CombinedClickableElement".equals(type)) {
                            isClickable = true;
                        } else if ("androidx.compose.foundation.ScrollingLayoutElement".equals(type)) {
                            isScrollable = true;
                        }
                    }
                }

                if (isClickable && targetType == ViewTarget.Type.Clickable) {
                    targetTag.set(lastKnownTag.get());
                }
                if (isScrollable && targetType == ViewTarget.Type.Scrollable) {
                    targetTag.set(lastKnownTag.get());
                    // skip any children for scrollable targets
                    return false;
                }
            }
            return true;
        });

        if (targetTag.get() == null) {
            return null;
        } else {
            return new ViewTarget(null, null, null, targetTag.get(), SOURCE);
        }
    }

    private static boolean layoutNodeBoundsContain(
            @NotNull ComposeLayoutNodeBoundsHelper composeLayoutNodeBoundsHelper,
            @NotNull LayoutNode node,
            final float x,
            final float y) {
        return boundsContain(composeLayoutNodeBoundsHelper.getLayoutNodeWindowBounds(node), x, y);
    }

    private static boolean boundsContain(
            @Nullable Rect bounds,
            final float x,
            final float y) {
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
