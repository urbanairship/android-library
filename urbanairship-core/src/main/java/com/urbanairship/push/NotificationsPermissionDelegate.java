/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.app.Activity;
import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.SimpleActivityListener;
import com.urbanairship.permission.PermissionDelegate;
import com.urbanairship.permission.PermissionRequestResult;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsActivity;
import com.urbanairship.push.notifications.NotificationChannelRegistry;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Consumer;

/**
 * The default permission delegate for notifications.
 */
class NotificationsPermissionDelegate implements PermissionDelegate {

    private final static String PROMPTED_KEY = "NotificationsPermissionDelegate.prompted";
    private final static String POST_NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS";

    private final String defaultChannelId;
    private final PreferenceDataStore dataStore;
    private final NotificationChannelRegistry channelRegistry;
    private final AirshipNotificationManager notificationManager;
    private final PermissionRequestDelegate permissionRequestDelegate;
    private final ActivityMonitor activityMonitor;

    interface PermissionRequestDelegate {

        void requestPermissions(@NonNull Context context, @NonNull String permission, @Nullable Consumer<PermissionRequestResult> consumer);

    }

    NotificationsPermissionDelegate(@NonNull String defaultChannelId,
                                    @NonNull PreferenceDataStore dataStore,
                                    @NonNull AirshipNotificationManager notificationManager,
                                    @NonNull NotificationChannelRegistry channelRegistry,
                                    @NonNull ActivityMonitor activityMonitor) {
        this(defaultChannelId, dataStore, notificationManager, channelRegistry, activityMonitor, PermissionsActivity::requestPermission);
    }

    @VisibleForTesting
    NotificationsPermissionDelegate(@NonNull String defaultChannelId,
                                    @NonNull PreferenceDataStore dataStore,
                                    @NonNull AirshipNotificationManager notificationManager,
                                    @NonNull NotificationChannelRegistry channelRegistry,
                                    @NonNull ActivityMonitor activityMonitor,
                                    @NonNull PermissionRequestDelegate permissionRequestDelegate) {
        this.defaultChannelId = defaultChannelId;
        this.dataStore = dataStore;
        this.notificationManager = notificationManager;
        this.channelRegistry = channelRegistry;
        this.activityMonitor = activityMonitor;
        this.permissionRequestDelegate = permissionRequestDelegate;
    }

    @Override
    public void checkPermissionStatus(@NonNull Context context, @NonNull Consumer<PermissionStatus> callback) {
        PermissionStatus status;

        if (notificationManager.areNotificationsEnabled()) {
            status = PermissionStatus.GRANTED;
        } else {
            switch (notificationManager.getPromptSupport()) {
                case COMPAT:
                case SUPPORTED:
                    if (dataStore.getBoolean(PROMPTED_KEY, false)) {
                        status = PermissionStatus.DENIED;
                    } else {
                        status = PermissionStatus.NOT_DETERMINED;
                    }
                    break;
                case NOT_SUPPORTED:
                default:
                    status = PermissionStatus.DENIED;
                    break;
            }
        }

        callback.accept(status);
    }

    @MainThread
    public void requestPermission(@NonNull Context context, @NonNull Consumer<PermissionRequestResult> callback) {
        if (notificationManager.areNotificationsEnabled()) {
            callback.accept(PermissionRequestResult.granted());
            return;
        }

        switch (notificationManager.getPromptSupport()) {
            case NOT_SUPPORTED:
                callback.accept(PermissionRequestResult.denied(true));
                return;

            case COMPAT:
                dataStore.put(PROMPTED_KEY, true);
                if (!notificationManager.areChannelsCreated()) {
                    channelRegistry.getNotificationChannel(defaultChannelId);
                    activityMonitor.addActivityListener(new SimpleActivityListener() {
                        @Override
                        public void onActivityResumed(@NonNull Activity activity) {
                            if (notificationManager.areNotificationsEnabled()) {
                                callback.accept(PermissionRequestResult.granted());
                            } else {
                                callback.accept(PermissionRequestResult.denied(false));
                            }
                            activityMonitor.removeActivityListener(this);
                        }
                    });
                } else {
                    callback.accept(PermissionRequestResult.denied(true));
                }
                return;

            case SUPPORTED:
                dataStore.put(PROMPTED_KEY, true);
                this.permissionRequestDelegate.requestPermissions(context, POST_NOTIFICATION_PERMISSION, callback);
        }
    }

}
