package com.amplitude.android.utilities

import android.net.Network
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.ref.WeakReference

class AndroidNetworkStateTest {
    private val network = mockk<Network>()
    private val otherNetwork = mockk<Network>()
    private val delegate = RecordingNetworkChangeCallback()
    private val delegateRef =
        WeakReference<AndroidNetworkListener.NetworkChangeCallback>(delegate)

    @Test
    fun `creation should notify available when available and not blocked`() {
        AndroidNetworkState(network, delegateRef, available = true, blocked = false)

        assertEquals(listOf(true), delegate.notifications)
    }

    @Test
    fun `creation should notify unavailable when not available`() {
        AndroidNetworkState(network, delegateRef, available = false, blocked = false)

        assertEquals(listOf(false), delegate.notifications)
    }

    @Test
    fun `creation should notify unavailable when blocked`() {
        AndroidNetworkState(network, delegateRef, available = true, blocked = true)

        assertEquals(listOf(false), delegate.notifications)
    }

    @Test
    fun `update should notify when availability toggles`() {
        val state = AndroidNetworkState(network, delegateRef, available = true, blocked = false)

        state.update(network, available = false)
        state.update(network, available = true)

        assertEquals(listOf(true, false, true), delegate.notifications)
    }

    @Test
    fun `update should notify when blocked toggles`() {
        val state = AndroidNetworkState(network, delegateRef, available = true, blocked = false)

        state.update(network, blocked = true)
        state.update(network, blocked = false)

        assertEquals(listOf(true, false, true), delegate.notifications)
    }

    @Test
    fun `update should not notify when nothing toggles`() {
        val state = AndroidNetworkState(network, delegateRef, available = true, blocked = false)

        state.update(network, available = true, blocked = false)

        assertEquals(listOf(true), delegate.notifications)
    }

    @Test
    fun `update should ignore other networks`() {
        val state = AndroidNetworkState(network, delegateRef, available = true, blocked = false)

        state.update(otherNetwork, available = false)

        assertEquals(listOf(true), delegate.notifications)
    }

    @Test
    fun `update should not notify a collected delegate`() {
        val state = AndroidNetworkState(network, delegateRef, available = true, blocked = false)

        delegateRef.clear()
        state.update(network, available = false)

        assertEquals(listOf(true), delegate.notifications)
    }

    private class RecordingNetworkChangeCallback : AndroidNetworkListener.NetworkChangeCallback {
        /** One entry per notification: true for available, false for unavailable. */
        val notifications = mutableListOf<Boolean>()

        override fun onNetworkAvailable() {
            notifications.add(true)
        }

        override fun onNetworkUnavailable() {
            notifications.add(false)
        }
    }
}
