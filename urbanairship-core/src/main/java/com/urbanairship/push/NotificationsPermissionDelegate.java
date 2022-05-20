package com.urbanairship.push;

import android.content.Context;

import com.urbanairship.permission.PermissionDelegate;
import com.urbanairship.permission.PermissionStatus;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.util.Consumer;

class NotificationsPermissionDelegate implements PermissionDelegate {

    private final Function<Context, Boolean> notificationsEnabledCheck;

    NotificationsPermissionDelegate() {
        this(input -> NotificationManagerCompat.from(input).areNotificationsEnabled());
    }

    NotificationsPermissionDelegate(Function<Context, Boolean> notificationsEnabledCheck) {
        this.notificationsEnabledCheck = notificationsEnabledCheck;
    }
    @Override
    public void checkPermissionStatus(@NonNull Context context, @NonNull Consumer<PermissionStatus> callback) {
        if (notificationsEnabledCheck.apply(context)) {
            callback.accept(PermissionStatus.GRANTED);
        } else {
            callback.accept(PermissionStatus.DENIED);
        }
    }

    @MainThread
    public void requestPermission(@NonNull Context context, @NonNull Consumer<PermissionStatus> callback) {
        checkPermissionStatus(context, callback);
    }
}
