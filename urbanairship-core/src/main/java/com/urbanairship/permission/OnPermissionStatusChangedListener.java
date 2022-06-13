/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import androidx.annotation.NonNull;

/**
 * Permission status changed listener.
 */
public interface OnPermissionStatusChangedListener {

    /**
     * Called when a permission status changes during an app run. When a delegate is set, the permission
     * status will be determined. If that status changes during the app lifecycle, it will be notified
     * through the delegate.
     *
     * @param permission The permission.
     * @param status The permission status.
     */
    void onPermissionStatusChanged(@NonNull Permission permission, @NonNull PermissionStatus status);
}
