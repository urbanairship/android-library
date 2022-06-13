/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.app.SimpleActivityListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.arch.core.util.Function;
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
    private final Map<Permission, PermissionStatus> permissionStatusMap = new HashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<OnPermissionStatusChangedListener> onPermissionStatusChangedListeners = new CopyOnWriteArrayList<>();
    private final Map<PermissionDelegate, PendingResult<PermissionRequestResult>> pendingRequestResults = new HashMap<>();
    private final Map<PermissionDelegate, PendingResult<PermissionStatus>> pendingCheckResults = new HashMap<>();

    private PermissionsManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static PermissionsManager newPermissionsManager(@NonNull Context context) {
        return newPermissionsManager(context, GlobalActivityMonitor.shared(context));
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public static PermissionsManager newPermissionsManager(@NonNull Context context, @NonNull ActivityMonitor activityMonitor) {
        PermissionsManager permissionsManager = new PermissionsManager(context);
        activityMonitor.addActivityListener(new SimpleActivityListener() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                permissionsManager.updatePermissions();
            }
        });
        return permissionsManager;
    }

    @MainThread
    private void updatePermissions() {
        for (Permission permission : getConfiguredPermissions()) {
            checkPermissionStatus(permission, status -> updatePermissionStatus(permission, status));
        }
    }

    @MainThread
    private void updatePermissionStatus(@NonNull Permission permission, @NonNull PermissionStatus status) {
        PermissionStatus previous = permissionStatusMap.get(permission);
        if (previous != null && previous != status) {
            for (OnPermissionStatusChangedListener listener : this.onPermissionStatusChangedListeners) {
                listener.onPermissionStatusChanged(permission, status);
            }
        }
        permissionStatusMap.put(permission, status);
    }

    /**
     * Gets the set of permissions that have a delegate.
     *
     * @return The set of configured permissions.
     */
    @NonNull
    public Set<Permission> getConfiguredPermissions() {
        synchronized (permissionDelegateMap) {
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
        synchronized (permissionDelegateMap) {
            permissionDelegateMap.put(permission, delegate);
            checkPermissionStatus(permission);
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
     * Adds a permission status changed listener.
     *
     * @param listener The listener to remove.
     */
    public void addOnPermissionStatusChangedListener(@NonNull OnPermissionStatusChangedListener listener) {
        this.onPermissionStatusChangedListeners.add(listener);
    }

    /**
     * Removes a permission status changed listener.
     *
     * @param listener The listener to remove.
     */
    public void removeOnPermissionStatusChangedListener(@NonNull OnPermissionStatusChangedListener listener) {
        this.onPermissionStatusChangedListeners.remove(listener);
    }

    /**
     * Checks the current permission status.
     *
     * @param permission The permission.
     * @param callback The callback with the result.
     */
    public void checkPermissionStatus(@NonNull Permission permission, @NonNull Consumer<PermissionStatus> callback) {
        checkPermissionStatus(permission).addResultCallback(callback::accept);
    }

    /**
     * Checks the current permission status.
     *
     * @param permission The permission.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<PermissionStatus> checkPermissionStatus(@NonNull Permission permission) {
        Logger.debug("Checking permission for %s", permission);

        synchronized (pendingCheckResults) {
            return pendingOrCall(permission, pendingCheckResults, delegate -> {
                final PendingResult<PermissionStatus> pendingResult = new PendingResult<>();

                if (delegate == null) {
                    Logger.debug("No delegate for permission %s", permission);
                    pendingResult.setResult(PermissionStatus.NOT_DETERMINED);
                    return pendingResult;
                }

                synchronized (pendingCheckResults) {
                    pendingCheckResults.put(delegate, pendingResult);
                }

                mainHandler.post(() -> delegate.checkPermissionStatus(context, status -> {
                    Logger.debug("Check permission %s status result: %s", permission, status);
                    updatePermissionStatus(permission, status);
                    pendingResult.setResult(status);

                    synchronized (pendingCheckResults) {
                        pendingCheckResults.remove(delegate);
                    }
                }));

                return pendingResult;
            });
        }
    }

    /**
     * Requests a permission.
     *
     * @param permission The permission.
     * @param callback The callback.
     */
    public void requestPermission(@NonNull Permission permission, @NonNull Consumer<PermissionRequestResult> callback) {
        requestPermission(permission, false, callback);
    }

    /**
     * Requests a permission.
     *
     * @param permission The permission.
     * @param enableAirshipUsageOnGrant If granted, any Airship features that need the permission will
     * be enabled, e.g., enabling {@link com.urbanairship.PrivacyManager#FEATURE_PUSH} and
     * {@link com.urbanairship.push.PushManager#setUserNotificationsEnabled(boolean)} if the push permission
     * is granted.
     * @param callback The callback.
     */
    public void requestPermission(@NonNull Permission permission, boolean enableAirshipUsageOnGrant, @NonNull Consumer<PermissionRequestResult> callback) {
        requestPermission(permission, enableAirshipUsageOnGrant).addResultCallback(callback::accept);
    }

    /**
     * Requests a permission. If a delegate is not set to handle the permission {@link PermissionStatus#NOT_DETERMINED} will
     * be returned.
     *
     * @param permission The permission.
     * @return A pending result.
     */
    @NonNull
    public PendingResult<PermissionRequestResult> requestPermission(@NonNull Permission permission) {
        return requestPermission(permission, false);
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
     * @return A pending result.
     */
    @NonNull
    public PendingResult<PermissionRequestResult> requestPermission(@NonNull Permission permission, boolean enableAirshipUsageOnGrant) {
        Logger.debug("Requesting permission for %s", permission);

        synchronized (pendingRequestResults) {
            PendingResult<PermissionRequestResult> result = pendingOrCall(permission, pendingRequestResults, delegate -> {
                final PendingResult<PermissionRequestResult> pendingResult = new PendingResult<>();

                if (delegate == null) {
                    Logger.debug("No delegate for permission %s", permission);
                    pendingResult.setResult(PermissionRequestResult.notDetermined());
                    return pendingResult;
                }

                synchronized (pendingRequestResults) {
                    pendingRequestResults.put(delegate, pendingResult);
                }

                mainHandler.post(() -> delegate.requestPermission(context, requestResult -> {
                    Logger.debug("Permission %s request result: %s", permission, requestResult);
                    updatePermissionStatus(permission, requestResult.getPermissionStatus());
                    pendingResult.setResult(requestResult);

                    synchronized (pendingRequestResults) {
                        pendingRequestResults.remove(delegate);
                    }
                }));

                return pendingResult;
            });

            if (enableAirshipUsageOnGrant) {
                result.addResultCallback(requestResult -> {
                    if (requestResult != null && requestResult.getPermissionStatus() == PermissionStatus.GRANTED) {
                        for (Consumer<Permission> enabler : this.airshipEnablers) {
                            enabler.accept(permission);
                        }
                    }
                });
            }

            return result;
        }
    }

    private PermissionDelegate getDelegate(Permission permission) {
        synchronized (permissionDelegateMap) {
            return permissionDelegateMap.get(permission);
        }
    }

    private <T> PendingResult<T> pendingOrCall(final Permission permission,
                                               final Map<PermissionDelegate, PendingResult<T>> pending,
                                               final Function<PermissionDelegate, PendingResult<T>> delegateFunction) {

        PermissionDelegate delegate = getDelegate(permission);
        if (delegate != null) {
            PendingResult<T> result = pending.get(delegate);
            if (result != null) {
                return result;
            }
        }
        return delegateFunction.apply(delegate);

    }

}
