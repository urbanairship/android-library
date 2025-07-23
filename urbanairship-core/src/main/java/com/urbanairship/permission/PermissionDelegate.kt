/* Copyright Airship and Contributors */
package com.urbanairship.permission

import android.content.Context
import androidx.annotation.MainThread
import androidx.core.util.Consumer

/**
 * Permission delegate to extend what permissions the Airship SDK can collect.
 */
public interface PermissionDelegate {

    /**
     * Checks the current status of the permission.
     *
     * @param context The application context.
     * @param callback The callback.
     */
    @MainThread
    public fun checkPermissionStatus(context: Context, callback: Consumer<PermissionStatus>)

    /**
     * Called when the delegate should request permissions.
     *
     * @param context The application context.
     * @param callback The callback.
     */
    @MainThread
    public fun requestPermission(context: Context, callback: Consumer<PermissionRequestResult>)
}
