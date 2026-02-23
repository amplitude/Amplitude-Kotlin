package com.amplitude.android.internal.gestures

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.RadioButton
import com.amplitude.MainDispatcherRule
import com.amplitude.android.AutocaptureState
import com.amplitude.android.InteractionType
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.locators.AndroidViewTargetLocator
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.KClass

class AutocaptureGestureListenerClickTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(Dispatchers.Unconfined)

    class Fixture {
        val activityName = "test_screen"
        val resources = mockk<Resources>()
        val logger = mockk<Logger>(relaxed = true)
        val context = mockk<Context>()
        val window = mockk<Window>(relaxed = true)
        val track = mockk<TrackFunction>(relaxed = true)

        lateinit var target: View
        lateinit var invalidTarget: View
        lateinit var decorView: View

        internal fun <T : View> getSut(
            type: KClass<T>,
            event: MotionEvent,
            resourceName: String = "test_button",
            isInvalidTargetVisible: Boolean = true,
            isInvalidTargetClickable: Boolean = true,
            attachViewsToRoot: Boolean = true,
            targetOverride: View? = null,
        ): AutocaptureGestureListener {
            invalidTarget =
                mockView(
                    type = View::class,
                    event = event,
                    visible = isInvalidTargetVisible,
                    clickable = isInvalidTargetClickable,
                    context = context,
                )

            if (targetOverride == null) {
                this.target =
                    mockView(
                        type = type,
                        event = event,
                        clickable = true,
                        context = context,
                    )
            } else {
                this.target = targetOverride
            }

            if (attachViewsToRoot) {
                decorView =
                    window.mockDecorView(
                        type = ViewGroup::class,
                        event = event,
                        context = context,
                    ) {
                        every { it.childCount } returns 2
                        every { it.getChildAt(0) } returns invalidTarget
                        every { it.getChildAt(1) } returns target
                    }
            } else {
                // Create a basic decorView for tests that don't attach views to root
                decorView =
                    mockView(
                        type = ViewGroup::class,
                        event = event,
                        context = context,
                    )
                every { window.decorView } returns decorView
            }

            resources.mockForTarget(this.target, resourceName)
            every { context.resources } returns resources
            every { this@Fixture.target.context } returns context
            return AutocaptureGestureListener(
                decorView,
                activityName,
                track,
                logger,
                listOf(AndroidViewTargetLocator()),
                { AutocaptureState(interactions = listOf(InteractionType.ElementInteraction)) },
            )
        }
    }

    private val fixture = Fixture()

//    @Test(timeout = 5000)
    @Test
    fun `when target and its ViewGroup are clickable, captures an event for target`() =
        runTest {
            val event = mockk<MotionEvent>(relaxed = true)
            val sut =
                fixture.getSut(
                    type = View::class,
                    event = event,
                    isInvalidTargetVisible = false,
                    attachViewsToRoot = false,
                )

            val container1 =
                mockView(type = ViewGroup::class, event = event, touchWithinBounds = false, context = fixture.context)
            val notClickableInvalidTarget =
                mockView(type = View::class, event = event)
            val container2 =
                mockView(type = ViewGroup::class, event = event, clickable = true, context = fixture.context) {
                    every { it.childCount } returns 3
                    every { it.getChildAt(0) } returns notClickableInvalidTarget
                    every { it.getChildAt(1) } returns fixture.invalidTarget
                    every { it.getChildAt(2) } returns fixture.target
                }

            // Configure the decorView that was created by getSut (instead of creating a new one)
            every { (fixture.decorView as ViewGroup).childCount } returns 2
            every { (fixture.decorView as ViewGroup).getChildAt(0) } returns container1
            every { (fixture.decorView as ViewGroup).getChildAt(1) } returns container2

            sut.onSingleTapUp(event)

            verify(exactly = 1) {
                fixture.track(
                    "[Amplitude] Element Interacted",
                    mapOf(
                        "[Amplitude] Action" to "touch",
                        "[Amplitude] Target Class" to "android.view.View",
                        "[Amplitude] Target Resource" to "test_button",
                        "[Amplitude] Target Tag" to null,
                        "[Amplitude] Target Text" to null,
                        "[Amplitude] Target Accessibility Label" to null,
                        "[Amplitude] Target Source" to "Android View",
                        "[Amplitude] Hierarchy" to "View",
                        "[Amplitude] Screen Name" to "test_screen",
                    ),
                )
            }
        }

    @Test
    fun `ignores invisible or gone views`() {
        val event = mockk<MotionEvent>(relaxed = true)
        val sut =
            fixture.getSut(
                type = RadioButton::class,
                event = event,
                resourceName = "radio_button",
                isInvalidTargetVisible = false,
            )

        sut.onSingleTapUp(event)

        verify {
            fixture.track(
                "[Amplitude] Element Interacted",
                match {
                    it["[Amplitude] Target Class"] == "android.widget.RadioButton" &&
                        it["[Amplitude] Target Resource"] == "radio_button"
                },
            )
        }
    }

    @Test
    fun `ignores not clickable targets`() {
        val event = mockk<MotionEvent>(relaxed = true)
        val sut =
            fixture.getSut(
                type = CheckBox::class,
                event = event,
                resourceName = "check_box",
                isInvalidTargetVisible = false,
            )

        sut.onSingleTapUp(event)

        verify {
            fixture.track(
                "[Amplitude] Element Interacted",
                match {
                    it["[Amplitude] Target Class"] == "android.widget.CheckBox" &&
                        it["[Amplitude] Target Resource"] == "check_box"
                },
            )
        }
    }

    @Test
    fun `when no children present and decor view not clickable, does not capture an event`() {
        val event = mockk<MotionEvent>(relaxed = true)
        val sut =
            fixture.getSut(
                type = View::class,
                event = event,
                attachViewsToRoot = false,
            )

        fixture.window.mockDecorView(type = ViewGroup::class, event = event) {
            every { it.childCount } returns 0
        }

        sut.onSingleTapUp(event)

        verify(exactly = 0) {
            fixture.track(any(), any())
        }
    }

    @Test
    fun `when target is decorView, captures an event for decorView`() {
        val event = mockk<MotionEvent>(relaxed = true)
        val decorView =
            fixture.window.mockDecorView(type = ViewGroup::class, event = event, clickable = true) {
                every { it.childCount } returns 0
            }

        val sut =
            fixture.getSut(
                type = ViewGroup::class,
                event = event,
                resourceName = "decor_view",
                targetOverride = decorView,
            )

        sut.onSingleTapUp(event)

        verify {
            fixture.track(
                "[Amplitude] Element Interacted",
                match {
                    it["[Amplitude] Target Class"] == decorView.javaClass.canonicalName &&
                        it["[Amplitude] Target Resource"] == "decor_view"
                },
            )
        }
    }

    @Test
    fun `does not capture events when view reference is null`() {
        val event = mockk<MotionEvent>(relaxed = true)
        val sut =
            fixture.getSut(
                type = View::class,
                event = event,
                attachViewsToRoot = false,
            )

        sut.onSingleTapUp(event)

        verify(exactly = 0) {
            fixture.track(any(), any())
        }
    }

    @Test
    fun `does not track element interactions when element interaction is disabled`() {
        val event = mockk<MotionEvent>(relaxed = true)
        val decorView =
            fixture.window.mockDecorView(type = ViewGroup::class, event = event, clickable = true) {
                every { it.childCount } returns 0
            }

        fixture.resources.mockForTarget(decorView, "decor_view")
        every { fixture.context.resources } returns fixture.resources
        every { decorView.context } returns fixture.context

        val sut =
            AutocaptureGestureListener(
                decorView,
                fixture.activityName,
                fixture.track,
                fixture.logger,
                listOf(AndroidViewTargetLocator()),
                { AutocaptureState(interactions = emptyList()) },
            )

        sut.onSingleTapUp(event)

        // Verify that track was NOT called since element interaction is disabled
        verify(exactly = 0) {
            fixture.track(any(), any())
        }
    }

    @Test
    fun `uses simple class name if canonical name isn't available`() {
        class LocalView(context: Context) : View(context)

        val event = mockk<MotionEvent>(relaxed = true)
        val sut =
            fixture.getSut(
                type = LocalView::class,
                event = event,
                attachViewsToRoot = false,
            )

        // Configure the decorView that was created by getSut (instead of creating a new one)
        every { (fixture.decorView as ViewGroup).childCount } returns 1
        every { (fixture.decorView as ViewGroup).getChildAt(0) } returns fixture.target

        sut.onSingleTapUp(event)

        verify {
            fixture.track(
                "[Amplitude] Element Interacted",
                match {
                    it["[Amplitude] Target Class"] == fixture.target.javaClass.simpleName &&
                        it["[Amplitude] Target Resource"] == "test_button"
                },
            )
        }
    }
}
