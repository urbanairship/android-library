/* Copyright Airship and Contributors */
package com.urbanairship.permission

/**
 * Permission status changed listener.
 */
public fun interface OnPermissionStatusChangedListener {

    /**
     * Called when a permission status changes during an app run. When a delegate is set, the permission
     * status will be determined. If that status changes during the app lifecycle, it will be notified
     * through the delegate.
     *
     * @param permission The permission.
     * @param status The permission status.
     */
    public fun onPermissionStatusChanged(permission: Permission, status: PermissionStatus)
}
