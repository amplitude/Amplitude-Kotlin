package com.amplitude.android.utilities

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowUtilsTest {
    @Test
    fun `first false is emitted and only consecutive duplicates are suppressed`() =
        runTest {
            val values = flowOf(false, false, true, true, false).onChanged().toList()
            assertEquals(listOf(false, true, false), values)
        }

    @Test
    fun `first null and subsequent changes to null are emitted`() =
        runTest {
            val values = flowOf(null, null, "value", "value", null).onChanged().toList()
            assertEquals(listOf(null, "value", null), values)
        }

    @Test
    fun `each collection has independent history`() =
        runTest {
            val values = flowOf(false, false).onChanged()
            assertEquals(listOf(false), values.toList())
            assertEquals(listOf(false), values.toList())
        }

    @Test
    fun `key comparison preserves the original transition timestamp`() =
        runTest {
            val first = ProcessLifecycleObserver.Transition(100, true)
            val duplicate = ProcessLifecycleObserver.Transition(200, true)
            val background = ProcessLifecycleObserver.Transition(300, false)
            val values = flowOf(first, duplicate, background).onChanged { it.foreground }.toList()
            assertEquals(listOf(first, background), values)
        }
}
