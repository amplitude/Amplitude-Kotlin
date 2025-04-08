package com.amplitude.core.utils

import com.amplitude.core.Amplitude
import com.amplitude.core.Configuration
import com.amplitude.core.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope

@OptIn(ExperimentalCoroutinesApi::class)
class FakeAmplitude(
    configuration: Configuration = Configuration("FAKE-API-KEY"),
    store: State = State(),
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
    amplitudeScope: CoroutineScope = TestScope(testDispatcher),
    amplitudeDispatcher: CoroutineDispatcher = testDispatcher,
    networkIODispatcher: CoroutineDispatcher = testDispatcher,
    storageIODispatcher: CoroutineDispatcher = testDispatcher,
) : Amplitude(
    configuration = configuration,
    store = store,
    amplitudeScope = amplitudeScope,
    amplitudeDispatcher = amplitudeDispatcher,
    networkIODispatcher = networkIODispatcher,
    storageIODispatcher = storageIODispatcher
)
