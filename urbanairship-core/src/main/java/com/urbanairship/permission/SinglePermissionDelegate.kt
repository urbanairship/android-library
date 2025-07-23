/* Copyright Airship and Contributors */
package com.urbanairship.permission

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.urbanairship.UALog

/**
 * Permission delegate that handles sa single Android permission.
 */
public class SinglePermissionDelegate public constructor(
    private val permission: String
) : PermissionDelegate {

    override fun checkPermissionStatus(context: Context, callback: Consumer<PermissionStatus>) {
        try {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                callback.accept(PermissionStatus.GRANTED)
            } else {
                callback.accept(PermissionStatus.DENIED)
            }
        } catch (e: Exception) {
            UALog.e(e, "Failed to get permission status.")
            callback.accept(PermissionStatus.NOT_DETERMINED)
        }
    }

    override fun requestPermission(context: Context, callback: Consumer<PermissionRequestResult>) {
        PermissionsActivity.requestPermission(context, permission, callback)
    }
}
