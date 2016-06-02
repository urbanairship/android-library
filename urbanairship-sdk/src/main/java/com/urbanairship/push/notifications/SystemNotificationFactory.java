/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.support.annotation.NonNull;

import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

/**
 * Notification factory that creates notifications with no default styles or fallback layouts.
 */
public class SystemNotificationFactory extends DefaultNotificationFactory {

    /**
     * System notification constructor.
     * @param context The application context.
     */
    public SystemNotificationFactory(Context context) {
        super(context);
    }

    @Override
    public Notification createNotification(@NonNull PushMessage message, int notificationId) {
        // do not display a notification if there is not an alert
        if (UAStringUtil.isEmpty(message.getAlert())) {
            return null;
        }

        return createNotificationBuilder(message, notificationId, null).build();
    }
}
