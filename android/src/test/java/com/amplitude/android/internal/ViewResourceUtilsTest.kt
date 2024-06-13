package com.amplitude.android.internal

import android.content.Context
import android.content.res.Resources
import android.view.View
import com.amplitude.android.internal.ViewResourceUtils.resourceId
import com.amplitude.android.internal.ViewResourceUtils.resourceIdWithFallback
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ViewResourceUtilsTest {
    @Test
    fun `getResourceId returns resourceId when available`() {
        val view =
            mockk<View> {
                every { id } returns View.generateViewId()

                val context = mockk<Context>()
                val resources = mockk<Resources>()
                every { resources.getResourceEntryName(id) } returns "test_view"
                every { context.resources } returns resources
                every { this@mockk.context } returns context
            }

        assertEquals(view.resourceId, "test_view")
    }

    @Test
    fun `getResourceId throws when resource id is not available`() {
        val view =
            mockk<View> {
                every { id } returns View.generateViewId()

                val context = mockk<Context>()
                val resources = mockk<Resources>()
                every { resources.getResourceEntryName(any()) } throws Resources.NotFoundException()
                every { context.resources } returns resources
                every { this@mockk.context } returns context
            }

        assertFailsWith<Resources.NotFoundException> { view.resourceId }
    }

    @Test
    fun `when view has no id set, resource name is not looked up `() {
        val context = mockk<Context>()
        val resources = mockk<Resources>()
        every { context.resources } returns resources

        val view =
            mockk<View> {
                every { id } returns View.NO_ID
                every { this@mockk.context } returns context
            }

        assertFailsWith<Resources.NotFoundException> { view.resourceId }
        verify(exactly = 0) { context.resources }
    }

    @Test
    fun `when view id is generated, resource name is not looked up `() {
        val context = mockk<Context>()
        val resources = mockk<Resources>()
        every { context.resources } returns resources

        val view =
            mockk<View> {
                // View.generateViewId() starts with 1
                every { id } returns 1
                every { this@mockk.context } returns context
            }

        assertFailsWith<Resources.NotFoundException> { view.resourceId }
        verify(exactly = 0) { context.resources }
    }

    @Test
    fun `getResourceIdWithFallback falls back to hexadecimal id when resource not found`() {
        val view =
            mockk<View> {
                every { id } returns 1234

                val context = mockk<Context>()
                val resources = mockk<Resources>()
                every { resources.getResourceEntryName(id) } throws Resources.NotFoundException()
                every { context.resources } returns resources
                every { this@mockk.context } returns context
            }

        assertEquals(view.resourceIdWithFallback, "0x4d2")
    }
}
