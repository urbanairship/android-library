/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import android.content.Context;
import android.content.pm.PackageManager;

import com.urbanairship.Logger;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

/**
 * Permission delegate that handles sa single Android permission.
 */
public class SinglePermissionDelegate implements PermissionDelegate {

    private final String permission;

    /**
     * Default constructor.
     *
     * @param permission The name of the Android permission.
     */
    public SinglePermissionDelegate(@NonNull String permission) {
        this.permission = permission;
    }

    @NonNull
    @Override
    public void checkPermissionStatus(@NonNull Context context, @NonNull Consumer<PermissionStatus> callback) {
        try {
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                callback.accept(PermissionStatus.GRANTED);
            }
            callback.accept(PermissionStatus.DENIED);
        } catch (Exception e) {
            Logger.error(e, "Failed to get permission status.");
            callback.accept(PermissionStatus.NOT_DETERMINED);
        }
    }

    @Override
    public void requestPermission(@NonNull Context context, @NonNull Consumer<PermissionRequestResult> callback) {
        PermissionsActivity.requestPermission(context, permission, callback);
    }
}
