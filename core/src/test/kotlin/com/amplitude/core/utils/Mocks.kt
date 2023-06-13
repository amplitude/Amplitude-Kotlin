package com.amplitude.core.utils

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.State
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@ExperimentalCoroutinesApi
fun testAmplitude(configuration: Configuration): Amplitude {
    val testDispatcher = UnconfinedTestDispatcher()
    val testScope = TestScope(testDispatcher)
    return object : Amplitude(configuration, State(), testScope, testDispatcher, testDispatcher, testDispatcher, testDispatcher) {}
}
