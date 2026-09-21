package com.amplitude.android.streaming.sample

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val Context.isTelevision: Boolean
    get() = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

@Composable
internal fun Modifier.tvFocusIndicator(): Modifier {
    var isFocused by remember { mutableStateOf(false) }

    return this
        .onFocusChanged { isFocused = it.isFocused }
        .scale(if (isFocused) 1.04f else 1f)
        .border(
            width = 3.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            shape = RoundedCornerShape(percent = 50),
        )
}
