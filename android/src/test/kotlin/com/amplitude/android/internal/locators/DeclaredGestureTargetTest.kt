package com.amplitude.android.internal.locators

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.SeekBar
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.internal.ViewHierarchyScanner.findTarget
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.gestures.GestureActions
import com.amplitude.android.internal.resolvedFor
import com.amplitude.common.Logger
import io.mockk.mockk
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeclaredGestureTargetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a long press on a clickable child is not reported on its long-clickable parent`() {
        val parent = FrameLayout(context)
        parent.isLongClickable = true
        val child = Button(context)
        parent.addView(child)

        val hit = parent.layoutAndFindTarget(child)

        assertSame(child, hit?.view)
        assertNull(hit?.resolvedFor(GestureActions.LONG_PRESS))
    }

    @Test
    fun `a tap on a slider is not reported on its clickable parent`() {
        val parent = FrameLayout(context)
        parent.isClickable = true
        val slider = SeekBar(context)
        slider.isClickable = false
        parent.addView(slider)

        val hit = parent.layoutAndFindTarget(slider)

        assertSame(slider, hit?.view)
        assertNull(hit?.resolvedFor(GestureActions.TOUCH))
    }

    @Test
    fun `a slider inside a scroll view declares pan`() {
        val scrollView = ScrollView(context)
        val slider = SeekBar(context)
        slider.isClickable = false
        scrollView.addView(slider)

        val hit = scrollView.layoutAndFindTarget(slider)

        assertSame(slider, hit?.resolvedFor(GestureActions.PAN)?.view)
    }

    private fun View.layoutAndFindTarget(child: View): ViewTarget? {
        layout(0, 0, 200, 200)
        child.layout(0, 0, 200, 200)
        return findTarget(Pair(20f, 20f), listOf(AndroidViewTargetLocator()), ViewTarget.Type.Clickable, mockk<Logger>(relaxed = true))
    }
}
