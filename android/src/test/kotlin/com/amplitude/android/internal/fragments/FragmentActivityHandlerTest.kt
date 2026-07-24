package com.amplitude.android.internal.fragments

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.amplitude.android.internal.fragments.FragmentActivityHandler.registerFragmentLifecycleCallbacks
import com.amplitude.android.internal.fragments.FragmentActivityHandler.unregisterFragmentLifecycleCallbacks
import com.amplitude.common.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class FragmentActivityHandlerTest {
    @Test
    fun `unregister removes callbacks only for the matching tracker`() {
        val activity = mockk<FragmentActivity>()
        val fragmentManager = mockk<FragmentManager>(relaxed = true)
        val logger = mockk<Logger>(relaxed = true)
        val registeredCallbacks = mutableListOf<FragmentManager.FragmentLifecycleCallbacks>()
        val firstTracker = Tracker()
        val secondTracker = Tracker()

        every { activity.supportFragmentManager } returns fragmentManager
        every {
            fragmentManager.registerFragmentLifecycleCallbacks(capture(registeredCallbacks), true)
        } returns Unit

        activity.registerFragmentLifecycleCallbacks(firstTracker::track, logger)
        activity.registerFragmentLifecycleCallbacks(secondTracker::track, logger)

        val firstCallback = registeredCallbacks[0]
        val secondCallback = registeredCallbacks[1]

        activity.unregisterFragmentLifecycleCallbacks(firstTracker::track, logger)

        verify(exactly = 1) {
            fragmentManager.unregisterFragmentLifecycleCallbacks(firstCallback)
        }
        verify(exactly = 0) {
            fragmentManager.unregisterFragmentLifecycleCallbacks(secondCallback)
        }

        activity.unregisterFragmentLifecycleCallbacks(secondTracker::track, logger)

        verify(exactly = 1) {
            fragmentManager.unregisterFragmentLifecycleCallbacks(secondCallback)
        }
    }

    private class Tracker {
        private val events = mutableListOf<Pair<String, Map<String, Any?>>>()

        fun track(
            eventType: String,
            eventProperties: Map<String, Any?>,
        ) {
            events += eventType to eventProperties
        }
    }
}
