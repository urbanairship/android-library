/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

/**
 * A notification factory that allows the use of layout XML. The default binding will
 * bind the following:
 *  - small icon to {@code android.R.id.icon}
 *  - title to {@code android.R.id.title}
 *  - summary/subtitle to {@code android.R.id.summary}
 *  - alert/message to {@code android.R.id.message}
 *
 * Custom binding can be applied by overriding {@link #onBindContentView(RemoteViews, PushMessage, int)}. To
 * customize the builder, override {@link #extendBuilder(NotificationCompat.Builder, PushMessage, int)}.
 */
public class CustomLayoutNotificationFactory extends NotificationFactory {

    private final int layoutId;

    /**
     * Default constructor.
     * @param context The application context.
     * @param layoutId The custom content view.
     */
    public CustomLayoutNotificationFactory(@NonNull Context context, @LayoutRes int layoutId) {
        super(context);
        this.layoutId = layoutId;
    }

    @Nullable
    @Override
    public final Notification createNotification(@NonNull PushMessage message, int notificationId) {
        if (UAStringUtil.isEmpty(message.getAlert())) {
            return null;
        }

        RemoteViews contentView = new RemoteViews(getContext().getPackageName(), layoutId);
        onBindContentView(contentView, message, notificationId);

        NotificationCompat.Builder builder = createNotificationBuilder(message, notificationId, null)
                .setCustomContentView(contentView);

        return extendBuilder(builder, message, notificationId).build();
    }

    /**
     * Called to apply any final overrides to the builder before the notification is built.
     *
     * @param builder The notification builder.
     * @param message The push message.
     * @param notificationId The notification ID.
     * @return The notification builder.
     */
    public NotificationCompat.Builder extendBuilder(@NonNull NotificationCompat.Builder builder, @NonNull PushMessage message, int notificationId) {
        return builder;
    }


    /**
     * Called to bind the content view to the push message.
     *
     * @param contentView The custom content view.
     * @param pushMessage The push message.
     * @param notificationId The notification ID.
     */
    public void onBindContentView(@NonNull RemoteViews contentView, @NonNull PushMessage pushMessage, int notificationId) {
        contentView.setTextViewText(android.R.id.title, pushMessage.getTitle() != null ? pushMessage.getTitle() : UAirship.getAppName());
        contentView.setTextViewText(android.R.id.message, pushMessage.getAlert());
        contentView.setTextViewText(android.R.id.summary, pushMessage.getSummary());
        contentView.setImageViewResource(android.R.id.icon, getSmallIconId());
    }
}
