package com.amplitude.android.internal.gestures

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.test.core.app.ApplicationProvider
import com.amplitude.android.AutocaptureState
import com.amplitude.android.FrustrationInteractionsDetector
import com.amplitude.android.internal.TrackFunction
import com.amplitude.android.internal.ViewTarget
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
