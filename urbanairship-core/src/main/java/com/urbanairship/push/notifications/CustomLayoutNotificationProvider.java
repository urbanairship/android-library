package com.urbanairship.push.notifications;

import android.content.Context;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import android.widget.RemoteViews;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;

/**
 * A notification provider that allows the use of layout XML. The default binding will
 * bind the following:
 * - small icon to {@code android.R.id.icon}
 * - title to {@code android.R.id.title}
 * - summary/subtitle to {@code android.R.id.summary}
 * - alert/message to {@code android.R.id.message}
 *
 * Custom binding can be applied by overriding {@link #onBindContentView(RemoteViews, NotificationArguments)}.
 */
public class CustomLayoutNotificationProvider extends AirshipNotificationProvider {

    private final int layoutId;

    public CustomLayoutNotificationProvider(@NonNull Context context,
                                            @NonNull AirshipConfigOptions configOptions,
                                            @LayoutRes int layoutId) {
        super(context, configOptions);

        this.layoutId = layoutId;
    }

    @NonNull
    @Override
    protected NotificationCompat.Builder onExtendBuilder(@NonNull Context context,
                                                         @NonNull NotificationCompat.Builder builder,
                                                         @NonNull NotificationArguments arguments) {

        RemoteViews contentView = new RemoteViews(context.getPackageName(), layoutId);
        onBindContentView(contentView, arguments);

        return builder.setCustomContentView(contentView);
    }

    /**
     * Called to bind the content view.
     *
     * @param contentView The custom content view.
     * @param arguments The notification arguments.
     */
    protected void onBindContentView(@NonNull RemoteViews contentView, @NonNull NotificationArguments arguments) {
        PushMessage message = arguments.getMessage();
        contentView.setTextViewText(android.R.id.title, message.getTitle() != null ? message.getTitle() : UAirship.getAppName());
        contentView.setTextViewText(android.R.id.message, message.getAlert());
        contentView.setTextViewText(android.R.id.summary, message.getSummary());
        contentView.setImageViewResource(android.R.id.icon, getSmallIcon());
    }

}
