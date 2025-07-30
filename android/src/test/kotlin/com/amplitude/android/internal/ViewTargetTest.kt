package com.amplitude.android.internal

import com.amplitude.android.internal.FrustrationConstants.IGNORE_FRUSTRATION_TAG
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewTargetTest {

    @Test
    fun `isIgnoredForFrustration - returns false when no ignore flags are set`() {
        val viewTarget = ViewTarget(
            _view = null,
            className = "TestView",
            resourceName = "test_resource",
            tag = "normal_tag",
            text = "Test",
            source = "android_view",
            hierarchy = "Activity → TestView",
            ampIgnoreRageClick = false,
            ampIgnoreDeadClick = false
        )

        assertFalse(viewTarget.isIgnoredForFrustration)
    }

    @Test
    fun `isIgnoredForFrustration - returns true for XML view with ignore frustration tag`() {
        val viewTarget = ViewTarget(
            _view = null,
            className = "TestView",
            resourceName = "test_resource",
            tag = IGNORE_FRUSTRATION_TAG,
            text = "Test",
            source = "android_view",
            hierarchy = "Activity → TestView",
            ampIgnoreRageClick = false,
            ampIgnoreDeadClick = false
        )

        assertTrue(viewTarget.isIgnoredForFrustration)
    }

    @Test
    fun `isIgnoredForFrustration - returns true for Compose view with both ignore flags true`() {
        val viewTarget = ViewTarget(
            _view = null,
            className = "androidx.compose.material3.Button",
            resourceName = null,
            tag = null,
            text = "Compose Button",
            source = "jetpack_compose",
            hierarchy = "Activity → ComposableScreen → Button",
            ampIgnoreRageClick = true,
            ampIgnoreDeadClick = true
        )

        assertTrue(viewTarget.isIgnoredForFrustration)
    }

    @Test
    fun `isIgnoredForFrustration - returns false for Compose view with only rage click ignored`() {
        val viewTarget = ViewTarget(
            _view = null,
            className = "androidx.compose.material3.IconButton",
            resourceName = null,
            tag = null,
            text = "Icon Button",
            source = "jetpack_compose",
            hierarchy = "Activity → ComposableScreen → IconButton",
            ampIgnoreRageClick = true,
            ampIgnoreDeadClick = false
        )

        assertFalse(viewTarget.isIgnoredForFrustration)
    }

    @Test
    fun `isIgnoredForFrustration - returns false for Compose view with only dead click ignored`() {
        val viewTarget = ViewTarget(
            _view = null,
            className = "androidx.compose.material3.FloatingActionButton",
            resourceName = null,
            tag = null,
            text = "FAB",
            source = "jetpack_compose",
            hierarchy = "Activity → ComposableScreen → FloatingActionButton",
            ampIgnoreRageClick = false,
            ampIgnoreDeadClick = true
        )

        assertFalse(viewTarget.isIgnoredForFrustration)
    }

    @Test
    fun `isIgnoredForFrustration - returns false for Compose view with no ignore flags`() {
        val viewTarget = ViewTarget(
            _view = null,
            className = "androidx.compose.material3.TextField",
            resourceName = null,
            tag = null,
            text = "Input Field",
            source = "jetpack_compose",
            hierarchy = "Activity → ComposableScreen → TextField",
            ampIgnoreRageClick = false,
            ampIgnoreDeadClick = false
        )

        assertFalse(viewTarget.isIgnoredForFrustration)
    }

    @Test
    fun `isIgnoredForFrustration - XML tag takes precedence over Compose flags when both present`() {
        // This scenario could happen in hybrid apps with both XML and Compose
        val viewTarget = ViewTarget(
            _view = null,
            className = "TestView",
            resourceName = "test_resource",
            tag = IGNORE_FRUSTRATION_TAG,
            text = "Hybrid View",
            source = "android_view",
            hierarchy = "Activity → TestView",
            ampIgnoreRageClick = false,
            ampIgnoreDeadClick = false
        )

        assertTrue(viewTarget.isIgnoredForFrustration)
    }

    @Test
    fun `isIgnoredForFrustration - works when both XML tag and Compose flags indicate ignore`() {
        val viewTarget = ViewTarget(
            _view = null,
            className = "TestView",
            resourceName = "test_resource",
            tag = IGNORE_FRUSTRATION_TAG,
            text = "View with all ignore flags",
            source = "android_view",
            hierarchy = "Activity → TestView",
            ampIgnoreRageClick = true,
            ampIgnoreDeadClick = true
        )

        assertTrue(viewTarget.isIgnoredForFrustration)
    }

    @Test
    fun `isIgnoredForFrustration - supports typical Compose component scenarios`() {
        // Test various Compose components that might use frustration analytics ignore
        val composableComponents = listOf(
            Triple("androidx.compose.material3.Card", "Card Content", "Activity → LazyColumn → Card"),
            Triple("androidx.compose.material3.Chip", "Filter Chip", "Activity → ChipGroup → Chip"),
            Triple("androidx.compose.foundation.layout.Box", "Custom Button", "Activity → Screen → Box"),
            Triple("androidx.compose.material3.ListItem", "List Item", "Activity → LazyColumn → ListItem")
        )

        composableComponents.forEach { (className, text, hierarchy) ->
            // Test with both flags true (should ignore)
            val ignoredComponent = ViewTarget(
                _view = null,
                className = className,
                resourceName = null,
                tag = null,
                text = text,
                source = "jetpack_compose",
                hierarchy = hierarchy,
                ampIgnoreRageClick = true,
                ampIgnoreDeadClick = true
            )
            assertTrue(ignoredComponent.isIgnoredForFrustration, 
                "Component $className should be ignored when both flags are true")

            // Test with flags false (should not ignore)
            val activeComponent = ViewTarget(
                _view = null,
                className = className,
                resourceName = null,
                tag = null,
                text = text,
                source = "jetpack_compose",
                hierarchy = hierarchy,
                ampIgnoreRageClick = false,
                ampIgnoreDeadClick = false
            )
            assertFalse(activeComponent.isIgnoredForFrustration,
                "Component $className should NOT be ignored when both flags are false")
        }
    }
}