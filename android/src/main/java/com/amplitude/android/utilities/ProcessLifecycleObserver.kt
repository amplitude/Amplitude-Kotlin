package com.amplitude.android.utilities

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** Observes process visibility without retaining its consumers in AndroidX. */
internal class ProcessLifecycleObserver(
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
    private val time: Time = Time(),
) {
    data class Transition(val timestamp: Long, val foreground: Boolean)

    private val started = AtomicBoolean(false)
    private var foreground = false

    private val observer = LifecycleObserver(this)

    // The plugin subscribes before start(), so replay is unnecessary. Buffer transitions
    // while its collector is busy without blocking lifecycle callbacks or dropping events.
    private val _events = MutableSharedFlow<Transition>(extraBufferCapacity = Int.MAX_VALUE)
    val events: SharedFlow<Transition> = _events.asSharedFlow()

    suspend fun start() =
        withContext(Dispatchers.Main) {
            if (!started.compareAndSet(false, true)) return@withContext

            foreground = false
            // addObserver synchronizes to the current state, including onStart when already
            // foregrounded. Do not synthesize another transition from currentState.
            lifecycle.addObserver(observer)
        }

    suspend fun stop() =
        withContext(NonCancellable + Dispatchers.Main) {
            if (!started.compareAndSet(true, false)) return@withContext

            lifecycle.removeObserver(observer)
        }

    @MainThread
    private fun transition(isForeground: Boolean) {
        if (foreground == isForeground) return
        foreground = isForeground
        _events.tryEmit(Transition(time.nowMillis(), isForeground))
    }

    private class LifecycleObserver(
        source: ProcessLifecycleObserver,
    ) : DefaultLifecycleObserver {
        private val source = WeakReference(source)

        override fun onStart(owner: LifecycleOwner) = transition(owner, true)

        override fun onStop(owner: LifecycleOwner) = transition(owner, false)

        private fun transition(
            owner: LifecycleOwner,
            foreground: Boolean,
        ) {
            val source = source.get()
            if (source == null) {
                owner.lifecycle.removeObserver(this)
            } else {
                source.transition(foreground)
            }
        }
    }
}
