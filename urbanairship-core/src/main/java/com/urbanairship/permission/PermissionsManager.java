/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.Consumer;

/**
 * Airship permission manager.
 *
 * Airship will provide the default handling for notifications. Other {@link Permission} needs to
 * be handled by the application by setting a delegate with {@link PermissionDelegate}.
 */
public class PermissionsManager {

    private final Context context;
    private final Map<Permission, PermissionDelegate> permissionDelegateMap = new HashMap<>();
    private final List<Consumer<Permission>> airshipEnablers = new CopyOnWriteArrayList<>();

    private final Object lock = new Object();
    Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public PermissionsManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Gets the set of permissions that have a delegate.
     *
     * @return The set of configured permissions.
     */
    @NonNull
    public Set<Permission> getConfiguredPermissions() {
        synchronized (lock) {
            return permissionDelegateMap.keySet();
        }
    }

    /**
     * Sets a delegate to handle permissions.
     *
     * @param permission The permission.
     * @param delegate The delegate.
     */
    public void setPermissionDelegate(@NonNull Permission permission, @Nullable PermissionDelegate delegate) {
        synchronized (lock) {
            permissionDelegateMap.put(permission, delegate);
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addAirshipEnabler(@NonNull Consumer<Permission> onEnable) {
        airshipEnablers.add(onEnable);
    }

    /**
     * Checks the current permission status.
     *
     * @param permission The permission.
     * @return The {@link PermissionStatus}.
     */
    @NonNull
    public PermissionStatus checkPermissionStatus(@NonNull Permission permission) {
        PermissionDelegate delegate = getDelegate(permission);
        return delegate == null ? PermissionStatus.NOT_DETERMINED : delegate.checkPermissionStatus(context);
    }

    /**
     * Requests a permission. If a delegate is not set to handle the permission {@link PermissionStatus#NOT_DETERMINED} will
     * be returned.
     *
     * @param permission The permission.
     * @param callback The callback with the result.
     */
    public void requestPermission(@NonNull Permission permission, @Nullable Consumer<PermissionStatus> callback) {
        requestPermission(permission, false, callback);
    }

    /**
     * Requests a permission. If a delegate is not set to handle the permission {@link PermissionStatus#NOT_DETERMINED} will
     * be returned.
     *
     * @param permission The permission.
     * @param enableAirshipUsageOnGrant If granted, any Airship features that need the permission will
     * be enabled, e.g., enabling {@link com.urbanairship.PrivacyManager#FEATURE_PUSH} and
     * {@link com.urbanairship.push.PushManager#setUserNotificationsEnabled(boolean)} if the push permission
     * is granted.
     * @param callback The callback with the result.
     */
    public void requestPermission(@NonNull Permission permission, boolean enableAirshipUsageOnGrant, @Nullable Consumer<PermissionStatus> callback) {
        Consumer<PermissionStatus> callbackWrapper = permissionStatus -> {
            if (enableAirshipUsageOnGrant && permissionStatus == PermissionStatus.GRANTED) {
                for (Consumer<Permission> enabler : this.airshipEnablers) {
                    enabler.accept(permission);
                }
            }

            if (callback != null) {
                callback.accept(permissionStatus);
            }
        };

        PermissionDelegate delegate = getDelegate(permission);
        mainHandler.post(() -> {
            if (delegate == null) {
                callbackWrapper.accept(PermissionStatus.NOT_DETERMINED);
            } else {
                if (delegate.checkPermissionStatus(context) == PermissionStatus.GRANTED) {
                    callbackWrapper.accept(PermissionStatus.GRANTED);
                } else {
                    delegate.requestPermission(context, callbackWrapper);
                }
            }
        });
    }

    private PermissionDelegate getDelegate(Permission permission) {
        synchronized (lock) {
            return permissionDelegateMap.get(permission);
        }
    }

}
