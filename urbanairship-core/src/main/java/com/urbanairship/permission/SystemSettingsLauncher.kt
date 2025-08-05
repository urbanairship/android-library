/* Copyright Airship and Contributors */

package com.urbanairship.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.MainThread
import com.urbanairship.UALog
import com.urbanairship.UAirship

internal class SystemSettingsLauncher {
    @MainThread
    fun openAppNotificationSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.applicationContext.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (e: ActivityNotFoundException) {
                UALog.i(e) { "Failed to launch notification settings." }
            }
        }

        try {
            context.applicationContext.startActivity(
                Intent("android.settings.APP_NOTIFICATION_SETTINGS")
                    .putExtra("app_package", context.packageName)
                    .putExtra("app_uid", UAirship.applicationContext.applicationInfo.uid)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (e: ActivityNotFoundException) {
            UALog.i(e) { "Failed to launch notification settings." }
        }

        return openAppSettings(context)
    }

    @MainThread
    fun openAppSettings(context: Context): Boolean {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setData(Uri.parse("package:" + context.packageName))
            )
            return true
        } catch (e: ActivityNotFoundException) {
            UALog.i(e) { "Unable to launch settings details activity." }
        }

        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_SETTINGS)
                    .addCategory(Intent.CATEGORY_DEFAULT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setData(Uri.parse("package:" + context.packageName))
            )
            return true
        } catch (e: ActivityNotFoundException) {
            UALog.i(e) { "Unable to launch settings activity." }
        }

        return false
    }
}
