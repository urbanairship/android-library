/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * Permission request result. See {@link PermissionsManager#requestPermission(Permission, boolean)}
 */
public class PermissionRequestResult {

    private PermissionStatus permissionStatus;
    private final boolean isSilentlyDenied;

    @VisibleForTesting
    PermissionRequestResult(@NonNull PermissionStatus permissionStatus, boolean isSilentlyDenied) {
        this.permissionStatus = permissionStatus;
        this.isSilentlyDenied = isSilentlyDenied;
    }

    /**
     * New granted result.
     *
     * @return The result.
     */
    @NonNull
    public static PermissionRequestResult granted() {
        return new PermissionRequestResult(PermissionStatus.GRANTED, false);
    }

    /**
     * New denied result.
     *
     * @param isSilentlyDenied If the permission prompt was not able to be displayed.
     * @return The result.
     */
    @NonNull
    public static PermissionRequestResult denied(boolean isSilentlyDenied) {
        return new PermissionRequestResult(PermissionStatus.DENIED, isSilentlyDenied);
    }

    /**
     * New not determined result.
     *
     * @return The result.
     */
    @NonNull
    public static PermissionRequestResult notDetermined() {
        return new PermissionRequestResult(PermissionStatus.NOT_DETERMINED, false);
    }

    /**
     * If the request failed to display the prompt.
     *
     * @return {@code true} if the prompt failed to display, otherwise {@code true}.
     */
    public boolean isSilentlyDenied() {
        return isSilentlyDenied;
    }

    /**
     * The permission status.
     *
     * @return The permission status.
     */
    @NonNull
    public PermissionStatus getPermissionStatus() {
        return permissionStatus;
    }

    @Override
    public String toString() {
        return "PermissionRequestResult{" +
                "permissionStatus=" + permissionStatus +
                ", isSilentlyDenied=" + isSilentlyDenied +
                '}';
    }

}
