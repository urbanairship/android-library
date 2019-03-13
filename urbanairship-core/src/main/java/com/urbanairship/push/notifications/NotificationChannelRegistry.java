package com.urbanairship.push.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipLoopers;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.UAirship;
import com.urbanairship.util.AirshipHandlerThread;

import java.util.concurrent.ExecutionException;

/**
 * Compatibility class for registering notification channels.
 */
public class NotificationChannelRegistry {

    /**
     * The registry database name.
     */
    private static final String DATABASE_NAME = "ua_notification_channel_registry.db";

    @VisibleForTesting
    final private NotificationChannelRegistryDataManager dataManager;

    private Handler backgroundHandler;
    private Context context;

    /**
     * NotificationChannelRegistry constructor.
     *
     * @hide
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     */
    public NotificationChannelRegistry(@NonNull Context context, @NonNull AirshipConfigOptions configOptions) {
        this(context, new NotificationChannelRegistryDataManager(context, configOptions.getAppKey(), DATABASE_NAME));
    }

    /**
     * NotificationChannelRegistry constructor.
     *
     * @hide
     *
     * @param context The application context.
     * @param dataManager The data manager.
     */
    NotificationChannelRegistry(@NonNull Context context, NotificationChannelRegistryDataManager dataManager) {
        this.context = context;
        this.dataManager = dataManager;
        this.backgroundHandler = new Handler(AirshipLoopers.getBackgroundLooper());

        migrateChannels();
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    private void migrateChannels() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationManager nm = getNotificationManager();
                    for (NotificationChannelCompat channelCompat : dataManager.getChannels()) {
                        String id = channelCompat.getId();
                        if (nm.getNotificationChannel(id) == null) {
                            Logger.debug("Migrating notification channel: %s", channelCompat.getName());
                            nm.createNotificationChannel(channelCompat.toNotificationChannel());
                        }
                    }
                }
            }
        });
    }

    /**
     * Gets a notification channel by identifier.
     *
     * @param id The notification channel identifier.
     * @return A PendingResult of NotificationChannelCompat.
     */
    public PendingResult<NotificationChannelCompat> getNotificationChannel(@NonNull final String id) {
        final PendingResult<NotificationChannelCompat> pendingResult = new PendingResult<>();

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                NotificationChannelCompat channelCompat = getNotificationChannelSync(id);
                pendingResult.setResult(channelCompat);
            }
        });

        return pendingResult;
    }

    /**
     * Gets a notification channel by identifier.
     * @param id The notification channel identifier.
     * @return A NotificationChannelCompat, or null if one could not be found.
     */
    @Nullable
    public NotificationChannelCompat getNotificationChannelSync(String id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = getNotificationManager().getNotificationChannel(id);

            if (channel != null) {
                NotificationChannelCompat channelCompat = new NotificationChannelCompat(channel);
                return channelCompat;
            }

        } else {
            return dataManager.getChannel(id);
        }

        return null;
    }

    /**
     * Creates a notification channel and saves it to disk. This method is a no-op if a channel
     * is already created with the same identifier. On Android O and above, this method
     * will also register an equivalent NotificationChannel with NotificationManager.
     *
     * @param channelCompat A NotificationChannelCompat.
     */
    public void createNotificationChannel(@NonNull final NotificationChannelCompat channelCompat) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel channel = channelCompat.toNotificationChannel();
                    getNotificationManager().createNotificationChannel(channel);
                }

                dataManager.createChannel(channelCompat);
            }
        });
    }

    /**
     * Deletes a notification channel, by identifier. On Android O and above, this method
     * will also delete the equivalent NotificationChannel on NotificationManager.
     *
     * @param id The notification channel identifier.
     */
    public void deleteNotificationChannel(@NonNull final String id) {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getNotificationManager().deleteNotificationChannel(id);
                }

                dataManager.deleteChannel(id);
            }
        });
    }
}
