package com.amplitude.android.utilities

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ParseException
import android.net.Uri
import android.os.Build
import com.amplitude.android.Amplitude

class DefaultEventUtils(private val amplitude: Amplitude) {
    object EventTypes {
        const val DEEP_LINK_OPENED = "[Amplitude] Deep Link Opened"
        const val SCREEN_VIEWED = "[Amplitude] Screen Viewed"
    }

    fun trackDeepLinkOpenedEvent(activity: Activity) {
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

    fun trackScreenViewedEvent(activity: Activity) {
        val packageManager = activity.packageManager
        try {
            val info = packageManager?.getActivityInfo(
                activity.componentName,
                PackageManager.GET_META_DATA
            )
            /* Get the label metadata in following order
              1. activity label
              2. if 1 is missing, fallback to parent application label
              3. if 2 is missing, use the activity name
             */
            val activityLabel = info?.loadLabel(packageManager)?.toString() ?: info?.name
            amplitude.track(EventTypes.SCREEN_VIEWED, mapOf("[Amplitude] Screen Name" to activityLabel))
        } catch (e: PackageManager.NameNotFoundException) {
            amplitude.logger.error("Failed to get activity info: $e")
        } catch (e: Exception) {
            amplitude.logger.error("Failed to track screen viewed event: $e")
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