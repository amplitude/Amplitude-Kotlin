package com.amplitude.android.utilities

import com.amplitude.android.Amplitude
import com.amplitude.android.Timeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

// simulates the dummy event for android lifecycle onActivityResumed
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.enterForeground(amplitude: Amplitude, timestamp: Long) = with(amplitude) {
    (timeline as Timeline).onEnterForeground(timestamp)
    advanceUntilIdle()
}

// simulates the dummy event for android lifecycle onActivityPaused
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.exitForeground(amplitude: Amplitude, timestamp: Long) = with(amplitude) {
    (timeline as Timeline).onExitForeground(timestamp)
    advanceUntilIdle()
}
