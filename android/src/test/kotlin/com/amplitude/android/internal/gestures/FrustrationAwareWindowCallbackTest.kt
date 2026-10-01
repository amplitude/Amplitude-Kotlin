package com.amplitude.android.internal.gestures

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.AutocaptureState
import com.amplitude.android.FrustrationInteractionsDetector
import com.amplitude.android.InteractionType.ElementInteraction
import com.amplitude.android.InteractionType.RageClick
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.ViewTarget
import com.amplitude.android.internal.locators.AndroidViewTargetLocator
import com.amplitude.common.Logger
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FrustrationAwareWindowCallbackTest {
    private val delegate = mockk<Window.Callback>(relaxed = true)
    private val decorView = View(ApplicationProvider.getApplicationContext())
    private val track = mockk<TrackFunction>(relaxed = true)
    private val logger = mockk<Logger>(relaxed = true)
    private val frustrationDetector = mockk<FrustrationInteractionsDetector>(relaxed = true)

    @Test
    fun `clears cached target when frustration tracking is disabled`() {
        val sut =
            FrustrationAwareWindowCallback(
                delegate = delegate,
                decorView = decorView,
                activityName = "TestActivity",
                track = track,
                viewTargetLocators = emptyList(),
                logger = logger,
                autocaptureStateProvider = { AutocaptureState(interactions = emptyList()) },
                frustrationDetector = frustrationDetector,
            )

        setLastFoundViewTarget(
            sut,
            ViewTarget(
                _view = decorView,
                className = "android.widget.Button",
                resourceName = "button",
                tag = null,
                text = "Tap me",
                accessibilityLabel = null,
                source = "android_view",
                hierarchy = null,
            ),
        )

        val now = SystemClock.uptimeMillis()
        val event =
            MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_UP,
                10f,
                20f,
                0,
            )

        sut.dispatchTouchEvent(event)

        assertNull(getLastFoundViewTarget(sut))
        verify(exactly = 0) {
            frustrationDetector.processClick(any(), any(), any(), any())
        }
        event.recycle()
    }

    @Test
    fun `does not record a tap on a drag-only control inside a clickable parent as a click`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = FrameLayout(context)
        root.isClickable = true
        val seekBar = SeekBar(context)
        seekBar.isClickable = false
        root.addView(seekBar)
        root.layout(0, 0, 200, 200)
        seekBar.layout(0, 0, 200, 200)

        val sut = callback(root, listOf(RageClick, ElementInteraction))

        sut.dispatchGesture(20f to 20f, 20f to 20f)

        verify(exactly = 0) {
            frustrationDetector.processClick(any(), any(), any(), any())
            track(any(), any())
        }
    }

    @Test
    fun `records a tap on a clickable control`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val button = Button(context)
        button.layout(0, 0, 200, 200)

        val sut = callback(button, listOf(RageClick))

        sut.dispatchGesture(20f to 20f, 20f to 20f)

        verify(exactly = 1) {
            frustrationDetector.processClick(any(), any(), any(), any())
        }
    }

    @Test
    fun `does not record a drag released over a clickable control as a click`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val button = Button(context)
        button.layout(0, 0, 200, 200)

        val sut = callback(button, listOf(RageClick))

        sut.dispatchGesture(20f to 20f, 20f to 150f)

        verify(exactly = 0) {
            frustrationDetector.processClick(any(), any(), any(), any())
        }
    }

    private fun callback(
        decor: View,
        interactions: List<com.amplitude.android.InteractionType>,
    ) = FrustrationAwareWindowCallback(
        delegate = delegate,
        decorView = decor,
        activityName = "TestActivity",
        track = track,
        viewTargetLocators = listOf(AndroidViewTargetLocator()),
        logger = logger,
        autocaptureStateProvider = { AutocaptureState(interactions = interactions) },
        frustrationDetector = frustrationDetector,
    )

    private fun FrustrationAwareWindowCallback.dispatchGesture(
        down: Pair<Float, Float>,
        up: Pair<Float, Float>,
    ) {
        val now = SystemClock.uptimeMillis()
        val events =
            listOf(
                MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, down.first, down.second, 0),
                MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_MOVE, up.first, up.second, 0),
                MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, up.first, up.second, 0),
            )
        events.forEach {
            dispatchTouchEvent(it)
            it.recycle()
        }
    }

    private fun setLastFoundViewTarget(
        callback: FrustrationAwareWindowCallback,
        target: ViewTarget?,
    ) {
        val field = AutocaptureWindowCallback::class.java.getDeclaredField("lastFoundViewTarget")
        field.isAccessible = true
        field.set(callback, target)
    }

    private fun getLastFoundViewTarget(callback: FrustrationAwareWindowCallback): ViewTarget? {
        val field = AutocaptureWindowCallback::class.java.getDeclaredField("lastFoundViewTarget")
        field.isAccessible = true
        return field.get(callback) as? ViewTarget
    }
}
