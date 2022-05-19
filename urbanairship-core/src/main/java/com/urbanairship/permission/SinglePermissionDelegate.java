/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import android.content.Context;
import android.content.pm.PackageManager;

import com.urbanairship.util.HelperActivity;

import androidx.annotation.MainThread;
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
    public PermissionStatus checkPermissionStatus(@NonNull Context context) {
        try {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return PermissionStatus.DENIED;
            }
            return PermissionStatus.GRANTED;
        } catch (Exception e) {
            return PermissionStatus.DENIED;
        }
    }

    @Override
    public void requestPermission(@NonNull Context context, @NonNull Consumer<PermissionStatus> callback) {
        HelperActivity.requestPermissions(context, new String[] { permission }, result -> {
            for (int i : result) {
                if (i == PackageManager.PERMISSION_GRANTED) {
                    callback.accept(PermissionStatus.GRANTED);
                    onPermissionGranted();
                    return;
                }
            }
            callback.accept(PermissionStatus.DENIED);
        });
    }

    /**
     * Called when the permission is granted.
     */
    @MainThread
    protected void onPermissionGranted() {
    }

}
