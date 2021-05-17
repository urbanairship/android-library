/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.annotation.XmlRes;
import androidx.core.app.NotificationCompat;

/**
 * Compatibility class for registering notification channels.
 */
public class NotificationChannelRegistry {

    /**
     * The registry database name.
     */
    private static final String DATABASE_NAME = "ua_notification_channel_registry.db";

    @VisibleForTesting
    private final NotificationChannelRegistryDataManager dataManager;
    private final Executor executor;
    private final Context context;
    private final NotificationManager notificationManager;

    /**
     * NotificationChannelRegistry constructor.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public NotificationChannelRegistry(@NonNull Context context, @NonNull AirshipConfigOptions configOptions) {
        this(context,
                new NotificationChannelRegistryDataManager(context, configOptions.appKey, DATABASE_NAME),
                AirshipExecutors.newSerialExecutor());
    }

    /**
     * NotificationChannelRegistry constructor.
     *
     * @param context The application context.
     * @param dataManager The data manager.
     * @hide
     */
    @VisibleForTesting
    NotificationChannelRegistry(@NonNull Context context, @NonNull NotificationChannelRegistryDataManager dataManager, @NonNull Executor executor) {
        this.context = context;
        this.dataManager = dataManager;
        this.executor = executor;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Gets a notification channel by identifier.
     *
     * @param id The notification channel identifier.
     * @return A PendingResult of NotificationChannelCompat.
     */
    @NonNull
    public PendingResult<NotificationChannelCompat> getNotificationChannel(@NonNull final String id) {
        final PendingResult<NotificationChannelCompat> pendingResult = new PendingResult<>();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                NotificationChannelCompat result;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = notificationManager.getNotificationChannel(id);
                    if (channel != null) {
                        result = new NotificationChannelCompat(channel);
                    } else {
                        result = dataManager.getChannel(id);
                        if (result == null) {
                            result = getAndCreateDefaultChannel(id);
                        }

                        if (result != null) {
                            notificationManager.createNotificationChannel(result.toNotificationChannel());
                        }
                    }
                } else {
                    result = dataManager.getChannel(id);
                    if (result == null) {
                        result = getAndCreateDefaultChannel(id);
                    }
                }

                pendingResult.setResult(result);
            }
        });

        return pendingResult;
    }

    /**
     * Gets a notification channel by identifier.
     *
     * @param id The notification channel identifier.
     * @return A NotificationChannelCompat, or null if one could not be found.
     */
    @Nullable
    @WorkerThread
    public NotificationChannelCompat getNotificationChannelSync(@NonNull String id) {
        try {
            return getNotificationChannel(id).get();
        } catch (InterruptedException e) {
            Logger.error(e, "Failed to get notification channel.");
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Logger.error(e, "Failed to get notification channel.");
        }

        return null;
    }

    /**
     * Deletes a notification channel, by identifier. On Android O and above, this method
     * will also delete the equivalent NotificationChannel on NotificationManager.
     *
     * @param id The notification channel identifier.
     */
    public void deleteNotificationChannel(@NonNull final String id) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notificationManager.deleteNotificationChannel(id);
                }
                dataManager.deleteChannel(id);
            }
        });
    }

    /**
     * Adds a notification channel and saves it to disk. This method is a no-op if a channel
     * is already created with the same identifier. On Android O and above, this method
     * will also create an equivalent NotificationChannel with NotificationManager.
     *
     * @param channelCompat A NotificationChannelCompat.
     */
    public void createNotificationChannel(@NonNull final NotificationChannelCompat channelCompat) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    notificationManager.createNotificationChannel(channelCompat.toNotificationChannel());
                }
                dataManager.createChannel(channelCompat);
            }
        });
    }

    /**
     * Like {@link #createNotificationChannel(NotificationChannelCompat)}, but on Android O and above,
     * the channel will not be created with the NotificationManager until it is accessed with
     * {@link #getNotificationChannel(String)}.
     *
     * @param channelCompat A NotificationChannelCompat.
     */
    public void createDeferredNotificationChannel(@NonNull final NotificationChannelCompat channelCompat) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                dataManager.createChannel(channelCompat);
            }
        });
    }


    /**
     * Creates notification channels from an XML file. Any channel that is already created
     * will no-op. On Android O and above, each channel will also create an equivalent
     * NotificationChannel with NotificationManager.
     *
     * The resource file can define all attributes on the channel:
     * <pre>
     * {@code
     * <resources>
     *     <NotificationChannel
     *         id="breaking_news"
     *         name="@string/breaking_news"
     *         description="@string/breaking_news_description"
     *         importance="3"
     *         can_bypass_dnd="false"
     *         can_show_badge="true"
     *         group="News"
     *         light_color="@color/blue"
     *         should_show_lights="true"
     *         should_vibrate="true"
     *         vibration_pattern="100,150,100" />
     * </resources>
     * }
     * </pre>
     *
     * @param resourceId The xml resource ID.
     * @hide
     */
    public void createNotificationChannels(@XmlRes final int resourceId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<NotificationChannelCompat> channelCompats = NotificationChannelCompat.fromXml(context, resourceId);
                for (NotificationChannelCompat channelCompat : channelCompats) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        notificationManager.createNotificationChannel(channelCompat.toNotificationChannel());
                    }

                    dataManager.createChannel(channelCompat);
                }
            }
        });
    }

    @WorkerThread
    private NotificationChannelCompat getAndCreateDefaultChannel(@NonNull String id) {
        List<NotificationChannelCompat> defaultChannels = NotificationChannelCompat.fromXml(context, R.xml.ua_default_channels);
        for (NotificationChannelCompat channel : defaultChannels) {
            if (id.equals(channel.getId())) {
                dataManager.createChannel(channel);
                return channel;
            }
        }

        return null;
    }

}
