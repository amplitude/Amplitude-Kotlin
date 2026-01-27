package com.amplitude.android.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.amplitude.core.diagnostics.DiagnosticsContextInfo
import com.amplitude.core.diagnostics.DiagnosticsContextProvider

class AndroidDiagnosticsContextProvider(
    private val context: Context,
) : DiagnosticsContextProvider {
    override suspend fun getContextInfo(): DiagnosticsContextInfo {
        return DiagnosticsContextInfo(
            manufacturer = Build.MANUFACTURER ?: "",
            model = Build.MODEL ?: "",
            osName = "Android",
            osVersion = Build.VERSION.RELEASE ?: "",
            platform = "Android",
            appVersion = getAppVersion(),
        )
    }

    private fun getAppVersion(): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: ""
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        } catch (_: Exception) {
            ""
        }
    }
}
