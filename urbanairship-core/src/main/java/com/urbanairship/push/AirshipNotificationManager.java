/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.app.NotificationManagerCompat;

/**
 * Airship notification manager wrapper.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AirshipNotificationManager {

    enum PromptSupport {
        NOT_SUPPORTED,
        COMPAT,
        SUPPORTED;
    }

    boolean areNotificationsEnabled();

    boolean areChannelsCreated();

    @NonNull
    PromptSupport getPromptSupport();

    static AirshipNotificationManager from(@NonNull Context context) {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        int targetSdkVersion = context.getApplicationInfo().targetSdkVersion;

        return new AirshipNotificationManager() {
            @Override
            public boolean areNotificationsEnabled() {
                return notificationManagerCompat.areNotificationsEnabled();
            }

            @Override
            public boolean areChannelsCreated() {
                return !notificationManagerCompat.getNotificationChannelsCompat().isEmpty();
            }

            @NonNull
            @Override
            public PromptSupport getPromptSupport() {
                if (Build.VERSION.SDK_INT >= 33) {
                    if (targetSdkVersion >= 33) {
                        return PromptSupport.SUPPORTED;
                    } else {
                        return PromptSupport.COMPAT;
                    }
                } else {
                    return PromptSupport.NOT_SUPPORTED;
                }
            }
        };
    }

}
