package com.amplitude.android.streaming.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Guards the launch mode [StreamingAnalytics.onDelayedEvent] relies on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UndispatchedPersistenceTest {
    @Test
    fun `a dispatched launch is dropped when teardown cancels the scope`() =
        runTest {
            val persisted = mutableListOf<Int>()
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

            scope.launch {
                withContext(NonCancellable) { persisted.add(1) }
            }
            scope.cancel()
            advanceUntilIdle()

            assertEquals(emptyList<Int>(), persisted)
        }

    @Test
    fun `an undispatched launch persists before teardown cancels the scope`() =
        runTest {
            val persisted = mutableListOf<Int>()
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                withContext(NonCancellable) { persisted.add(1) }
            }
            scope.cancel()
            advanceUntilIdle()

            assertEquals(listOf(1), persisted)
        }
}
