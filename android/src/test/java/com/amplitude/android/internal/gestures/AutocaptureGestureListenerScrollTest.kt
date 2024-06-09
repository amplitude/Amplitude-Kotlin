package com.amplitude.android.internal.gestures

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AbsListView
import android.widget.ListAdapter
import androidx.core.view.ScrollingView
import com.amplitude.android.internal.locators.AndroidViewTargetLocator
import com.amplitude.common.Logger
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class)
class AutocaptureGestureListenerScrollTest {
    class Fixture {
        val activity = mockk<Activity>()
        val resources = mockk<Resources>()
        val logger = mockk<Logger>(relaxed = true)
        val context = mockk<Context>()
        val window = mockk<Window>(relaxed = true)
        val track = mockk<(String, Map<String, Any?>) -> Unit>(relaxed = true)

        val firstEvent = mockk<MotionEvent>(relaxed = true)
        val eventsInBetween =
            listOf(
                mockk<MotionEvent>(relaxed = true),
                mockk(relaxed = true),
                mockk(relaxed = true),
            )
        val endEvent = eventsInBetween.last()
        val directions = setOf("up", "down", "left", "right")

        lateinit var target: View

        internal fun <T : View> getSut(
            type: KClass<T>,
            resourceName: String = "test_scroll_view",
            touchWithinBounds: Boolean = true,
            isAndroidXAvailable: Boolean = true,
            direction: String = "",
        ): AutocaptureGestureListener {
            target =
                mockView(
                    type = type,
                    event = firstEvent,
                    touchWithinBounds = touchWithinBounds,
                    context = context,
                )
            window.mockDecorView(type = ViewGroup::class, event = firstEvent) {
                every { it.childCount } returns 1
                every { it.getChildAt(0) } returns target
            }

            resources.mockForTarget(target, resourceName)
            every { context.resources } returns resources
            every { target.context } returns context

            if (direction in directions) {
                endEvent.mockDirection(firstEvent, direction)
            }
            every { activity.window } returns window
            return AutocaptureGestureListener(
                activity,
                track,
                logger,
                listOf(AndroidViewTargetLocator(isAndroidXAvailable)),
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
    fun `captures a scroll event`() {
        val sut = fixture.getSut(type = ScrollableListView::class, direction = "left")

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10.0f, 0f)
        }
        sut.onUp(fixture.endEvent)

        verify(exactly = 1) {
            fixture.track(
                "[Amplitude] Element Scrolled",
                mapOf(
                    "[Amplitude] Element Class" to fixture.target.javaClass.canonicalName,
                    "[Amplitude] Element Resource" to "test_scroll_view",
                    "[Amplitude] Element Tag" to null,
                    "[Amplitude] Element Source" to "Android View",
                    "[Amplitude] Direction" to "left",
                ),
            )
        }
    }

    @Test
    fun `if no target found, does not capture an event`() {
        val sut = fixture.getSut(type = ScrollableListView::class, touchWithinBounds = false)

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10f, 0f)
        }
        sut.onUp(fixture.endEvent)

        verify(exactly = 0) {
            fixture.track(any(), any())
        }
    }

    @Test
    fun `resets scroll state between gestures`() {
        val sut =
            fixture.getSut(
                type = ScrollableView::class,
                resourceName = "pager",
                direction = "down",
            )

        // first scroll down
        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween
            .forEach { sut.onScroll(fixture.firstEvent, it, 0f, 30.0f) }
        sut.onFling(fixture.firstEvent, fixture.endEvent, 1.0f, 1.0f)
        sut.onUp(fixture.endEvent)

        // second scroll up
        fixture.endEvent.mockDirection(fixture.firstEvent, "up")

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween
            .forEach { sut.onScroll(fixture.firstEvent, it, 0f, -30.0f) }
        sut.onFling(fixture.firstEvent, fixture.endEvent, 1.0f, 1.0f)
        sut.onUp(fixture.endEvent)

        verifyOrder {
            fixture.track(
                "[Amplitude] Element Swiped",
                mapOf(
                    "[Amplitude] Element Class" to fixture.target.javaClass.canonicalName,
                    "[Amplitude] Element Resource" to "pager",
                    "[Amplitude] Element Tag" to null,
                    "[Amplitude] Element Source" to "Android View",
                    "[Amplitude] Direction" to "down",
                ),
            )
            fixture.track(
                "[Amplitude] Element Swiped",
                mapOf(
                    "[Amplitude] Element Class" to fixture.target.javaClass.canonicalName,
                    "[Amplitude] Element Resource" to "pager",
                    "[Amplitude] Element Tag" to null,
                    "[Amplitude] Element Source" to "Android View",
                    "[Amplitude] Direction" to "up",
                ),
            )
        }
        confirmVerified(fixture.track)
    }

    @Test
    fun `if no scroll or swipe event occurred, does not capture an event`() {
        val sut = fixture.getSut(type = ScrollableView::class)
        sut.onUp(fixture.firstEvent)
        sut.onDown(fixture.endEvent)

        verify(exactly = 0) {
            fixture.track(any(), any())
        }
    }

    @Test
    fun `if androidX is not available, does not capture an event for ScrollingView`() {
        val sut = fixture.getSut(type = ScrollableView::class, isAndroidXAvailable = false)

        sut.onDown(fixture.firstEvent)
        fixture.eventsInBetween.forEach {
            sut.onScroll(fixture.firstEvent, it, 10.0f, 0f)
        }
        sut.onUp(fixture.endEvent)

        verify(exactly = 0) {
            fixture.track(any(), any())
        }
    }

    internal class ScrollableView : View(mockk()), ScrollingView {
        override fun computeVerticalScrollOffset(): Int = 0

        override fun computeVerticalScrollExtent(): Int = 0

        override fun computeVerticalScrollRange(): Int = 0

        override fun computeHorizontalScrollOffset(): Int = 0

        override fun computeHorizontalScrollRange(): Int = 0

        override fun computeHorizontalScrollExtent(): Int = 0
    }

    internal open class ScrollableListView : AbsListView(mockk()) {
        override fun getAdapter(): ListAdapter = mockk()

        override fun setSelection(position: Int) = Unit
    }

    companion object {
        private fun MotionEvent.mockDirection(
            firstEvent: MotionEvent,
            direction: String,
        ) {
            val initialStartX = firstEvent.x
            val initialStartY = firstEvent.y
            when (direction) {
                "up" -> {
                    every { x } returns initialStartX
                    every { y } returns (initialStartY - 2)
                }
                "down" -> {
                    every { x } returns initialStartX
                    every { y } returns (initialStartY + 2)
                }
                "right" -> {
                    every { x } returns (initialStartX + 2)
                    every { y } returns initialStartY
                }
                "left" -> {
                    every { x } returns (initialStartX - 2)
                    every { y } returns initialStartY
                }
            }
        }
    }
}
