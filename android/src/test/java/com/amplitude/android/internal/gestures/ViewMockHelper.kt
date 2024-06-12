package com.amplitude.android.internal.gestures

import android.content.Context
import android.content.res.Resources
import android.view.MotionEvent
import android.view.View
import android.view.Window
import io.mockk.every
import io.mockk.mockkClass
import kotlin.math.abs
import kotlin.reflect.KClass

internal fun <T : View> Window.mockDecorView(
    type: KClass<T>,
    id: Int = View.generateViewId(),
    event: MotionEvent,
    touchWithinBounds: Boolean = true,
    clickable: Boolean = false,
    visible: Boolean = true,
    context: Context? = null,
    finalize: (T) -> Unit = {},
): T {
    val view = mockView(type, id, event, touchWithinBounds, clickable, visible, context, finalize)
    every { decorView } returns view
    return view
}

internal fun <T : View> mockView(
    type: KClass<T>,
    id: Int = View.generateViewId(),
    event: MotionEvent,
    touchWithinBounds: Boolean = true,
    clickable: Boolean = false,
    visible: Boolean = true,
    context: Context? = null,
    finalize: (T) -> Unit = {},
): T {
    val coordinates = IntArray(2)
    if (!touchWithinBounds) {
        coordinates[0] = (event.x).toInt() + 10
        coordinates[1] = (event.y).toInt() + 10
    } else {
        coordinates[0] = (event.x).toInt() - 10
        coordinates[1] = (event.y).toInt() - 10
    }
    val mockView = mockkClass(type, relaxed = true)

    every { mockView.id } returns id
    every { mockView.context } returns context
    every { mockView.isClickable } returns clickable
    every { mockView.visibility } returns if (visible) View.VISIBLE else View.GONE

    every { mockView.getLocationOnScreen(any()) } answers {
        val array = invocation.args[0] as IntArray
        array[0] = coordinates[0]
        array[1] = coordinates[1]
    }

    val diffPosX = abs(event.x - coordinates[0]).toInt()
    val diffPosY = abs(event.y - coordinates[1]).toInt()
    every { mockView.width } returns diffPosX + 10
    every { mockView.height } returns diffPosY + 10

    finalize(mockView)

    return mockView
}

internal fun Resources.mockForTarget(
    target: View,
    expectedResourceName: String,
) {
    every { getResourceEntryName(target.id) } returns expectedResourceName
}
