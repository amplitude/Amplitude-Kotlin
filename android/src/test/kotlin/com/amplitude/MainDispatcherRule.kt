@file:OptIn(ExperimentalCoroutinesApi::class)

package com.amplitude

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit4 rule that swaps `Dispatchers.Main` for a [TestDispatcher] and
 * tears it down when the test finishes.
 *
 * Usage:
 * ```
 * @get:Rule
 * val mainDispatcherRule = MainDispatcherRule()
 *
 * @Test
 * fun someTest() = runTest {
 *     // Main dispatcher is a TestDispatcher here
 * }
 * ```
 */
class MainDispatcherRule(
    private val testDispatcher: CoroutineDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
