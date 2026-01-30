package com.example.focusguard.util

import android.content.Context
import android.content.pm.PackageManager

object AppUtils {
    /**
     * Resolves the human-readable application label from a package name.
     * Returns the package name itself if the app is not found or has no label.
     */
    fun getAppName(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName // Fallback to package name
        } catch (e: Exception) {
            packageName
        }
    }
}
