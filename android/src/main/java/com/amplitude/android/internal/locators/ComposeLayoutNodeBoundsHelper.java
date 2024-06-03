package com.amplitude.android.internal.locators;

import androidx.annotation.NonNull;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.LayoutCoordinatesKt;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.LayoutNodeLayoutDelegate;

import com.amplitude.common.Logger;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

@ApiStatus.Internal
@SuppressWarnings("KotlinInternalInJava")
public class ComposeLayoutNodeBoundsHelper {
  private Field layoutDelegateField = null;

  private final @NotNull Logger logger;

  public ComposeLayoutNodeBoundsHelper(@NonNull Logger logger) {
    this.logger = logger;
    try {
      final Class<?> clazz = Class.forName("androidx.compose.ui.node.LayoutNode");
      layoutDelegateField = clazz.getDeclaredField("layoutDelegate");
      layoutDelegateField.setAccessible(true);
    } catch (Exception e) {
      logger.info("Could not find LayoutNode.layoutDelegate field");
    }
  }

  public @Nullable Rect getLayoutNodeWindowBounds(@NotNull final LayoutNode node) {
    if (layoutDelegateField == null) {
      return null;
    }
    try {
      final LayoutNodeLayoutDelegate delegate = (LayoutNodeLayoutDelegate) layoutDelegateField.get(node);
        if (delegate == null) {
          return null;
        }
        return LayoutCoordinatesKt.boundsInWindow(delegate.getOuterCoordinator().getCoordinates());
    } catch (Exception e) {
      logger.info("Could not fetch position for LayoutNode");
    }
    return null;
  }
}
