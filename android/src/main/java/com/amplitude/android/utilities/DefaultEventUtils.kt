package com.amplitude.android.utilities

import android.app.Activity
import android.content.Intent
import android.net.ParseException
import android.net.Uri
import android.os.Build
import com.amplitude.android.Amplitude

class DefaultEventUtils(private val amplitude: Amplitude) {
    object EventTypes {
        const val DEEP_LINK_OPENED = "[Amplitude] Deep Link Opened"
    }

    fun trackDeepLinkEvent(activity: Activity) {
        val intent = activity.intent
        intent?.let {
            val referrer = getReferrer(activity)?.toString()
            val url = it.data?.toString()
            amplitude.track(
                EventTypes.DEEP_LINK_OPENED, mapOf(
                    "[Amplitude] Link URL" to url,
                    "[Amplitude] Link Referrer" to referrer
                )
            )
        }
    }

    private fun getReferrer(activity: Activity): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            return activity.referrer
        } else {
            var referrerUri: Uri? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val intent = activity.intent
                referrerUri = intent.getParcelableExtra(Intent.EXTRA_REFERRER)

                if (referrerUri == null) {
                    referrerUri = intent.getStringExtra("android.intent.extra.REFERRER_NAME")?.let {
                        try {
                            Uri.parse(it)
                        } catch (e: ParseException) {
                            null
                        }
                    }
                }
            }
            return referrerUri
        }
    }
}