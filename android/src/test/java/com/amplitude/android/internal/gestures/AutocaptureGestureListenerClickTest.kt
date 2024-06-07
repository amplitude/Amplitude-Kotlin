package com.amplitude.android.internal.gestures

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.RadioButton
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_CLASS
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_RESOURCE
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_SOURCE
import com.amplitude.android.utilities.DefaultEventUtils.EventProperties.ELEMENT_TAG
import com.amplitude.android.utilities.DefaultEventUtils.EventTypes.ELEMENT_TAPPED
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class AutocaptureGestureListenerClickTest {
    class Fixture {
        val activity = mockk<Activity>()
        val resources = mockk<Resources>()
        val logger = mockk<Logger>(relaxed = true)
        val context = mockk<Context>()
        val window = mockk<Window>(relaxed = true)
        val track = mockk<(String, Map<String, Any?>) -> Unit>(relaxed = true)

        lateinit var target: View
        lateinit var invalidTarget: View

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
                window.mockDecorView(
                    type = ViewGroup::class,
                    event = event,
                    context = context,
                ) {
                    every { it.childCount } returns 2
                    every { it.getChildAt(0) } returns invalidTarget
                    every { it.getChildAt(1) } returns target
                }
            }

            resources.mockForTarget(this.target, resourceName)
            every { context.resources } returns resources
            every { this@Fixture.target.context } returns context
            every { activity.window } returns window
            return AutocaptureGestureListener(
                activity,
                track,
                logger,
            )
        }
    }

    private val fixture = Fixture()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when target and its ViewGroup are clickable, captures an event for target`() {
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

        fixture.window.mockDecorView(
            type = ViewGroup::class,
            event = event,
            context = fixture.context,
        ) {
            every { it.childCount } returns 2
            every { it.getChildAt(0) } returns container1
            every { it.getChildAt(1) } returns container2
        }

        sut.onSingleTapUp(event)

        verify(exactly = 1) {
            fixture.track(
                ELEMENT_TAPPED,
                mapOf(
                    ELEMENT_CLASS to "android.view.View",
                    ELEMENT_RESOURCE to "test_button",
                    ELEMENT_TAG to null,
                    ELEMENT_SOURCE to "Android View",
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
                ELEMENT_TAPPED,
                mapOf(
                    ELEMENT_CLASS to "android.widget.RadioButton",
                    ELEMENT_RESOURCE to "radio_button",
                    ELEMENT_TAG to null,
                    ELEMENT_SOURCE to "Android View",
                ),
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
                ELEMENT_TAPPED,
                mapOf(
                    ELEMENT_CLASS to "android.widget.CheckBox",
                    ELEMENT_RESOURCE to "check_box",
                    ELEMENT_TAG to null,
                    ELEMENT_SOURCE to "Android View",
                ),
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
                ELEMENT_TAPPED,
                mapOf(
                    ELEMENT_CLASS to decorView.javaClass.canonicalName,
                    ELEMENT_RESOURCE to "decor_view",
                    ELEMENT_TAG to null,
                    ELEMENT_SOURCE to "Android View",
                ),
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
    fun `uses simple class name if canonical name isn't available`() {
        class LocalView(context: Context) : View(context)

        val event = mockk<MotionEvent>(relaxed = true)
        val sut =
            fixture.getSut(
                type = LocalView::class,
                event = event,
                attachViewsToRoot = false,
            )

        fixture.window.mockDecorView(type = ViewGroup::class, event = event, touchWithinBounds = false) {
            every { it.childCount } returns 1
            every { it.getChildAt(0) } returns fixture.target
        }

        sut.onSingleTapUp(event)

        verify {
            fixture.track(
                ELEMENT_TAPPED,
                mapOf(
                    ELEMENT_CLASS to fixture.target.javaClass.simpleName,
                    ELEMENT_RESOURCE to "test_button",
                    ELEMENT_TAG to null,
                    ELEMENT_SOURCE to "Android View",
                ),
            )
        }
    }
}
