package com.amplitude.android.streaming.sample

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational

internal fun Activity.enterPipIfPossible(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return false
    }
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
        return false
    }
    val params =
        PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
    return enterPictureInPictureMode(params)
}
